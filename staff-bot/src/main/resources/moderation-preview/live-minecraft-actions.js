'use strict';

let minecraftWorkflow = null;
const originalMinecraftBoundary = window.testEnvironmentBoundary;
window.testEnvironmentBoundary = function () {
  if (state.session?.staging !== false || !liveActionCapabilities?.minecraftEnabled) return originalMinecraftBoundary();
  return element('div',{className:'simulation-boundary'},
    element('strong',{text:'Live Minecraft moderation'}),
    element('span',{text:'Configured punishments apply to the network after confirmation or required staff approval. Discord enforcement is '
      + (liveActionCapabilities.discordEnabled ? 'enabled.' : 'disabled.') + ' Message deletion is unavailable.'}));
};
const originalLiveOpenWorkflow = window.openWorkflow;
const originalLiveRenderWorkflow = window.renderWorkflow;
window.renderWorkflow = function () {
  return minecraftWorkflow ? renderMinecraftPunishment() : originalLiveRenderWorkflow();
};
window.openWorkflow = function () {
  if (state.session?.staging !== false || !liveActionCapabilities?.minecraftEnabled) return originalLiveOpenWorkflow();
  if (state.deleting.size) return showToast('Clear message deletion selections before issuing a Minecraft punishment.', true);
  minecraftWorkflow = {target:'', reason:'', explanation:'', prepared:null, result:null, busy:false, uncertain:false};
  state.workflow = {minecraft:true};
  const accounts = liveModeration.bootstrap?.linkedAccounts || [];
  if (accounts.length === 1) minecraftWorkflow.target = accounts[0].playerId;
  if (liveActionCapabilities.discordEnabled) {
    $('#workflowTitle').textContent = 'Choose punishment scope';
    $('#workflowSteps').replaceChildren();
    $('#workflowBody').replaceChildren(element('p',{text:'Choose the account and service this punishment applies to.'}));
    const minecraft = buttonNode('Minecraft','button primary',{});
    minecraft.addEventListener('click',renderMinecraftPunishment);
    const discord = buttonNode('Discord','button secondary',{});
    discord.addEventListener('click',() => {
      minecraftWorkflow = null;
      $('#punishmentDialog').close();
      originalLiveOpenWorkflow();
    });
    $('#workflowFooter').replaceChildren(minecraft,discord);
    $('#punishmentDialog').showModal();
    return;
  }
  renderMinecraftPunishment();
  $('#punishmentDialog').showModal();
  $('#minecraftTarget')?.focus();
};

$('#punishmentDialog').addEventListener('cancel', event => {
  if (minecraftWorkflow?.busy) event.preventDefault();
});
$('#punishmentDialog').addEventListener('close', () => {
  minecraftWorkflow = null;
  $('#closeWorkflow').disabled = false;
});

function minecraftActionPayload(workflow, operation) {
  const payload = {targetKey:liveModeration.bootstrap?.targetKey, minecraftTarget:workflow.prepared?.targetId || workflow.target};
  if (operation === 'prepare') payload.minecraftIntent = {reasonId:workflow.reason, explanation:workflow.explanation};
  else payload.confirmationId = workflow.prepared.confirmationId;
  return payload;
}

async function performMinecraftAction(operation) {
  const workflow = minecraftWorkflow;
  if (!workflow || workflow.busy) return;
  workflow.busy = true;
  renderMinecraftPunishment();
  try {
    const result = await requestModerationAction(operation, minecraftActionPayload(workflow, operation));
    if (minecraftWorkflow !== workflow) return;
    if (operation === 'prepare') workflow.prepared = result;
    else { workflow.result = result; workflow.uncertain = false; }
  } catch (error) {
    if (operation === 'confirm' || operation === 'status') workflow.uncertain = true;
    showToast(operation === 'prepare' ? error.message
      : 'The action outcome is unconfirmed. Check this confirmation’s status before preparing another punishment.', true);
  } finally {
    workflow.busy = false;
    if (minecraftWorkflow === workflow) renderMinecraftPunishment();
  }
}

function renderMinecraftPunishment() {
  const workflow = minecraftWorkflow;
  if (!workflow) return;
  $('#workflowTitle').textContent = workflow.result && workflow.result.state !== 'PREPARED'
    ? 'Minecraft punishment status' : workflow.prepared ? 'Review Minecraft punishment' : 'Minecraft punishment';
  $('#workflowSteps').replaceChildren();
  $('#closeWorkflow').disabled = workflow.busy;
  $('#punishmentDialog').setAttribute('aria-busy', String(workflow.busy));
  const body = $('#workflowBody');
  const footer = $('#workflowFooter');
  body.replaceChildren(); footer.replaceChildren();
  body.append(element('p',{className:'muted',text:'Uses the network’s configured reasons, escalation rules, and current staff authority. Discord enforcement stays separate.'}));
  if (!workflow.prepared) {
    const target = element('input',{id:'minecraftTarget',value:workflow.target,placeholder:'Minecraft username or UUID',attrs:{maxlength:36,autocomplete:'off'}});
    const reasons = element('select',{id:'minecraftReason'},optionNode('','Select a configured reason',true));
    for (const reason of liveActionCapabilities.minecraftReasons || []) reasons.append(optionNode(reason.id, reason.family + ' — ' + reason.label, workflow.reason === reason.id));
    const explanation = element('textarea',{id:'minecraftExplanation',value:workflow.explanation,attrs:{maxlength:4000,rows:5},placeholder:'Internal explanation and evidence references'});
    target.addEventListener('input',() => { workflow.target = target.value.trim(); });
    reasons.addEventListener('change',() => { workflow.reason = reasons.value; });
    explanation.addEventListener('input',() => { workflow.explanation = explanation.value; });
    body.append(fieldLabel('Minecraft player',target),fieldLabel('Configured reason',reasons),fieldLabel('Internal explanation',explanation));
    const prepare = buttonNode(workflow.busy ? 'Preparing…' : 'Review punishment','button primary',{});
    prepare.disabled = workflow.busy;
    prepare.addEventListener('click',() => {
      if (!workflow.target || !workflow.reason) return showToast('Select a Minecraft player and configured reason.',true);
      performMinecraftAction('prepare');
    });
    footer.append(prepare);
    return;
  }
  const prepared = workflow.prepared;
  body.append(summaryList([['Minecraft player',prepared.targetName],['Player UUID',prepared.targetId],['Reason',prepared.reason],
    ['Consequences',(prepared.consequences || []).map(value => value.type + ' · ' + value.duration).join(', ')],
    ['Internal explanation',prepared.explanation || 'None']]));
  if (workflow.result && workflow.result.state !== 'PREPARED') {
    body.append(element('h3',{text:workflow.result.state === 'APPLIED' ? 'Punishment committed' : 'Approval requested'}),
      element('p',{text:workflow.result.state === 'APPLIED' ? 'Case: ' + workflow.result.caseId
        : 'Request: ' + workflow.result.requestId + '. No punishment is applied until an authorized reviewer approves it.'}));
    const done = buttonNode('Done','button primary',{}); done.addEventListener('click',closeWorkflow); footer.append(done);
    return;
  }
  body.append(element('p',{text:'Confirm before ' + new Date(prepared.expiresAt).toLocaleTimeString()
    + '. The server rechecks authority, target protection, and current policy when you confirm.'}));
  const status = buttonNode(workflow.busy ? 'Checking…' : 'Check status','button secondary',{});
  status.disabled = workflow.busy; status.addEventListener('click',() => performMinecraftAction('status')); footer.append(status);
  const confirm = buttonNode(workflow.busy ? 'Working…' : 'Confirm Minecraft punishment','button primary',{});
  confirm.disabled = workflow.busy || workflow.uncertain;
  confirm.addEventListener('click',() => performMinecraftAction('confirm')); footer.append(confirm);
}
