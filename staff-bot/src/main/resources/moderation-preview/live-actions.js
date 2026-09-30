'use strict';

let liveActionCapabilities = null;
const actionLoadSession = window.loadSession;
window.loadSession = async function () {
  await actionLoadSession();
  if (state.session?.staging !== false) return;
  try { liveActionCapabilities = await requestModerationAction('capabilities', {}); }
  catch { liveActionCapabilities = null; }
};

async function requestModerationAction(operation, input) {
  if (!state.session || !['capabilities','prepare','confirm','status'].includes(operation)) throw new Error('Session unavailable');
  const response = await fetch('/api/actions/' + operation, {
    method:'POST', cache:'no-store', headers:{'Content-Type':'application/json','X-Preview-Csrf':state.session.csrfToken},
    body:JSON.stringify(input)
  });
  if (!response.ok) throw new Error('Action session rejected. Reopen from Discord.');
  const proof = await response.json();
  if (proof.origin !== DIRECT_READ_ORIGIN || proof.path !== '/v1/moderation/actions/' + operation
      || proof.method !== 'POST' || !validDirectReadBody(proof.body) || !validDirectReadAuthentication(proof)) throw new Error('Action proof rejected');
  const destinations = {
    capabilities:'https://moderation-read-staging.enthusia.info/v1/moderation/actions/capabilities',
    prepare:'https://moderation-read-staging.enthusia.info/v1/moderation/actions/prepare',
    confirm:'https://moderation-read-staging.enthusia.info/v1/moderation/actions/confirm',
    status:'https://moderation-read-staging.enthusia.info/v1/moderation/actions/status'
  };
  const result = await fetch(destinations[operation], directReadRequest(proof));
  if (!result.ok) throw new Error(result.status === 403 ? 'Current staff authority denied this action.'
    : result.status === 400 ? 'Action rejected. Check the target, duration and permissions, then prepare again.'
    : 'Moderation service unavailable. Check status before submitting another action.');
  return result.json();
}

function liveActionInput(w) {
  if (!liveActionCapabilities?.discordEnabled) throw new Error('Discord enforcement is not enabled yet.');
  if (w.scope !== 'Discord') throw new Error('Minecraft enforcement has not passed activation checks.');
  if (state.deleting.size) throw new Error('Clear deletion selections. Message deletion is not enabled.');
  if (!w.dm) throw new Error('Live actions require a target notification.');
  const types = {Warning:'WARNING', Mute:'MUTE', Kick:'KICK', Ban:'BAN', Restrict:'CHANNEL_RESTRICTION'};
  const type = types[w.actual.action];
  if (!type) throw new Error('Unsupported action');
  const duration = type === 'WARNING' || type === 'KICK' ? 'instant' : actionDuration(w.duration);
  const evidence = [...state.evidence].map(id => 'Discord message reference: ' + id);
  if (w.externalEvidence) evidence.push('External evidence reference: ' + w.externalEvidence);
  const explanation = [w.reason, ...evidence].filter(Boolean).join('\n');
  if (explanation.length > 2000) throw new Error('Evidence references and explanation exceed 2000 characters.');
  const intent = {type, duration, reason:w.offense.label, explanation, restriction:null};
  if (type === 'CHANNEL_RESTRICTION') {
    const targets = restrictionTargetSelections(w);
    if (targets.length !== 1) throw new Error('Select exactly one channel or category per restriction.');
    intent.restriction = {kind:targets[0].type.toUpperCase(), snowflake:targets[0].id,
      mode:w.restrictMode === 'read-only' ? 'READ_ONLY' : 'NO_ACCESS'};
  }
  return {targetKey:liveModeration.bootstrap?.targetKey, intent};
}

function actionDuration(label) {
  if (label === 'Permanent') return 'permanent';
  const match = /^([1-9][0-9]*) (minutes?|hours?|days?)$/.exec(label);
  if (!match) throw new Error('Select a valid duration.');
  return match[1] + ({m:'m',h:'h',d:'d'}[match[2][0]]);
}

const simulationReviewStep = window.renderReviewStep;
window.renderReviewStep = function () {
  simulationReviewStep();
  if (state.session?.staging !== false) return;
  const w = state.workflow;
  const confirm = $('[data-confirm]');
  if (confirm) { confirm.disabled = true; confirm.textContent = 'Preparing live action…'; }
  w.livePrepared = null;
  try {
    const input = liveActionInput(w);
    requestModerationAction('prepare', input).then(prepared => {
      if (state.workflow !== w || w.step !== 'review') return;
      w.livePrepared = {...prepared, targetKey:input.targetKey};
      $('#workflowBody').append(element('div',{className:'alert warning'},
        element('strong',{text:'Server-prepared live action'}),
        element('span',{text:`${prepared.intent.type} · ${prepared.targetUserId} · ${prepared.intent.length.kind}. Target notifications are included. Authority is checked again on confirmation.`})));
      if (confirm) { confirm.disabled = false; confirm.textContent = 'Confirm live action'; }
    }).catch(error => { if (state.workflow === w) showToast(error.message, true); });
  } catch (error) {
    if (confirm) confirm.textContent = 'Live action unavailable';
    $('#workflowBody').append(element('div',{className:'alert warning',text:error.message}));
  }
};

const simulationConfirm = window.confirmSimulation;
window.confirmSimulation = async function () {
  if (state.session?.staging !== false) return simulationConfirm();
  const w = state.workflow;
  if (!w.livePrepared || w.submitting) return;
  if (w.stale || w.recommendationEvidenceRevision !== state.evidenceRevision) {
    showToast('Evidence changed. Recalculate and prepare the action again.',true); return;
  }
  w.submitting = true;
  $('[data-confirm]').disabled = true;
  const input = {targetKey:w.livePrepared.targetKey, confirmationId:w.livePrepared.confirmationId};
  try {
    w.liveStatus = await requestModerationAction('confirm', input);
    w.step = 'complete'; renderWorkflow();
    for (let attempt = 0; attempt < 12 && w.liveStatus.state === 'PENDING_APPLY'; attempt += 1) {
      await new Promise(resolve => setTimeout(resolve, 1500));
      w.liveStatus = await requestModerationAction('status', input);
      if (state.workflow === w) renderWorkflow();
    }
  } catch (error) {
    try { w.liveStatus = await requestModerationAction('status', input); w.step = 'complete'; renderWorkflow(); }
    catch { showToast(error.message + ' Do not submit a replacement until its status is checked.',true); }
  } finally { w.submitting = false; }
};

const simulationComplete = window.renderCompleteStep;
window.renderCompleteStep = function () {
  if (state.session?.staging !== false) return simulationComplete();
  const result = state.workflow?.liveStatus;
  $('#workflowTitle').textContent = 'Live action status';
  $('#workflowSteps').replaceChildren();
  replaceChildrenOf($('#workflowBody'), element('section',{className:'card'},
    element('h3',{text:result?.state || 'Status unavailable'}),
    element('p',{text:result?.externalApplied ? 'Discord applied the action.' : 'Discord has not confirmed the effect.'}),
    element('p',{text:'Target notification: ' + (result?.dmOutcome || 'Unknown')}),
    element('p',{text:'Punishment ID: ' + (result?.punishmentId || 'Unknown')})));
  replaceChildrenOf($('#workflowFooter'),buttonNode('Done','button primary',{done:''}));
  $('[data-done]').addEventListener('click',closeWorkflow);
};

const simulationBoundary = window.testEnvironmentBoundary;
window.testEnvironmentBoundary = function () {
  if (state.session?.staging !== false) return simulationBoundary();
  return element('div',{className:'simulation-boundary'},element('strong',{text:liveActionCapabilities?.discordEnabled ? 'Live Discord moderation' : 'Discord enforcement disabled'}),
    element('span',{text:liveActionCapabilities?.discordEnabled
      ? 'Confirming applies a durable Discord punishment and queues a target notification. Evidence references are recorded; message contents are not archived and no messages are deleted.'
      : 'Live messages and staff data are connected. Punishment confirmation is unavailable until the enforcement policy is activated.'}));
};

const simulationApprovalRequired = window.workflowApprovalRequired;
window.workflowApprovalRequired = function (workflow) {
  return state.session?.staging === false ? false : simulationApprovalRequired(workflow);
};
const simulationApprovalText = window.approvalReviewText;
window.approvalReviewText = function (workflow) {
  return state.session?.staging === false ? 'Current authority checked by the server' : simulationApprovalText(workflow);
};
const simulationScopeField = window.scopeField;
window.scopeField = function (workflow) {
  if (state.session?.staging !== false) return simulationScopeField(workflow);
  workflow.scope = 'Discord';
  return fieldLabel('Scope',element('select',{id:'customScope',disabled:true},optionNode('Discord','Discord',true)));
};
const simulationOffenseStep = window.renderOffenseStep;
window.renderOffenseStep = function () {
  simulationOffenseStep();
  if (state.session?.staging !== false) return;
  $('[data-offense-tab="game"]')?.remove();
};
