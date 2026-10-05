'use strict';

const catalogBaseOpenWorkflow = window.openWorkflow;
const catalogBaseRenderWorkflow = window.renderWorkflow;
const catalogBaseRenderOffenseStep = window.renderOffenseStep;
const catalogBaseRenderRecommendationStep = window.renderRecommendationStep;

function configuredPunishmentReasons() {
  const reasons = liveActionCapabilities?.configuredReasons;
  return Array.isArray(reasons) ? reasons : [];
}

function punishmentCatalogAvailable() {
  return configuredPunishmentReasons().length > 0;
}

function catalogFamilyLabel(family) {
  return String(family || 'other')
    .replace(/[._-]+/g,' ')
    .replace(/\b\w/g, letter => letter.toUpperCase());
}

function catalogFamilies() {
  const grouped = new Map();
  for (const reason of configuredPunishmentReasons()) {
    const family = String(reason.family || 'other');
    const current = grouped.get(family) || [];
    current.push(reason);
    grouped.set(family,current);
  }
  return [...grouped.entries()]
    .map(([family,reasons]) => ({family,label:catalogFamilyLabel(family),reasons:reasons.sort((a,b) => String(a.label).localeCompare(String(b.label)))}))
    .sort((a,b) => a.label.localeCompare(b.label));
}

function catalogRulesLink() {
  return element('a',{
    className:'policy-link catalog-rules-link',
    text:'Open Enthusia server rules',
    attrs:{href:'https://enthusia.info/rules',target:'_blank',rel:'noopener noreferrer'}
  });
}

function renderCatalogCategoryStep() {
  if (!punishmentCatalogAvailable()) return catalogBaseRenderOffenseStep();
  const workflow = state.workflow;
  $('#workflowTitle').textContent = 'Choose punishment category';
  const cards = catalogFamilies().map(group => {
    const button = buttonNode('', 'choice-card', {policyFamily:group.family});
    button.append(
      element('strong',{text:group.label}),
      element('span',{text:`${group.reasons.length} configured reason${group.reasons.length === 1 ? '' : 's'}`})
    );
    return button;
  });
  replaceChildrenOf($('#workflowBody'),
    stepIntro('What happened?','Choose the general category first, then select the exact configured reason.'),
    catalogRulesLink(),
    element('div',{className:'option-grid catalog-category-grid'},cards));
  replaceChildrenOf($('#workflowFooter'),buttonNode('Cancel','button ghost',{cancel:''}));
  $$('[data-policy-family]').forEach(button => button.addEventListener('click',() => {
    workflow.policyFamily = button.dataset.policyFamily;
    workflow.step = 'reason';
    renderWorkflow();
  }));
  $('[data-cancel]')?.addEventListener('click',closeWorkflow);
}

function renderCatalogReasonStep() {
  const workflow = state.workflow;
  const group = catalogFamilies().find(item => item.family === workflow.policyFamily);
  if (!group) {
    workflow.step = 'offense';
    renderWorkflow();
    return;
  }
  $('#workflowTitle').textContent = `${group.label} · exact reason`;
  renderWorkflowSteps('reason');
  const cards = group.reasons.map(reason => catalogReasonChoice(reason));
  replaceChildrenOf($('#workflowBody'),
    stepIntro('Choose the exact reason',`These are the configured ${group.label} reasons used by Enthusia's punishment policy.`),
    element('div',{className:'option-grid catalog-reason-grid'},cards));
  replaceChildrenOf($('#workflowFooter'),buttonNode('Back to categories','button ghost',{catalogBack:''}));
  $('[data-catalog-back]')?.addEventListener('click',() => {
    workflow.step = 'offense';
    renderWorkflow();
  });
  $$('[data-policy-reason]').forEach(button => button.addEventListener('click',() => chooseCatalogReason(button.dataset.policyReason)));
}

function catalogReasonChoice(reason) {
  const button = buttonNode('', 'choice-card catalog-reason-card', {policyReason:reason.id});
  const metadata = [
    `Severity ${reason.severity ?? '—'}/100`,
    `Required rank: ${catalogFamilyLabel(reason.requiredRank || 'MOD')}`,
    `${Array.isArray(reason.ladder) ? reason.ladder.length : 0} ladder step${reason.ladder?.length === 1 ? '' : 's'}`
  ];
  button.append(element('strong',{text:reason.label}),element('span',{text:metadata.join(' · ')}));
  if (reason.minecraftSupported === false) {
    button.append(element('small',{text:'Some configured consequences require the in-game workflow.'}));
  }
  return button;
}

function chooseCatalogReason(reasonId) {
  const workflow = state.workflow;
  const reason = configuredPunishmentReasons().find(item => item.id === reasonId);
  if (!reason) return;
  workflow.exactReasonId = reason.id;
  workflow.configuredReason = reason;
  workflow.offense = {key:reason.family,label:reason.label};
  workflow.recommendation = catalogRecommendation(reason);
  workflow.recommendationEvidenceRevision = state.evidenceRevision;
  workflow.step = 'recommendation';
  renderWorkflow();
}

function catalogRecommendation(reason) {
  const ladder = Array.isArray(reason.ladder) ? reason.ladder : [];
  const first = ladder[0] || {ordinal:0,label:'Custom review',consequences:[]};
  const consequence = representativeDiscordConsequence(first.consequences || []);
  return {
    action:consequence.action,
    scope:'Discord',
    duration:consequence.duration,
    relevant:[],
    relevantCount:typeof realRelevantHistoryCount === 'function' ? realRelevantHistoryCount(reason.family) : 0,
    total:typeof realHistoryTotal === 'function' ? realHistoryTotal() : state.history.length,
    step:Number(first.ordinal || 0) + 1,
    configuredStepLabel:first.label || 'Step 1',
    explanation:'Configured starting step for this exact reason. Review the full ladder and adjust the Discord action before confirmation when needed.'
  };
}

function representativeDiscordConsequence(consequences) {
  const supported = consequences.find(item => ['WARNING','KICK','MUTE','PUBLIC_MUTE','BAN','NETWORK_BAN','NETWORK_IDENTITY_BAN'].includes(item.type));
  if (!supported) return {action:'Warning',duration:'—'};
  if (supported.type === 'WARNING') return {action:'Warning',duration:'—'};
  if (supported.type === 'KICK') return {action:'Kick',duration:'—'};
  if (supported.type === 'MUTE' || supported.type === 'PUBLIC_MUTE') {
    return {action:'Mute',duration:catalogDuration(supported.duration)};
  }
  return {action:'Ban',duration:catalogDuration(supported.duration)};
}

function catalogDuration(value) {
  const normalized = String(value || '').trim();
  if (!normalized || normalized === 'instant') return '—';
  if (normalized.toLowerCase() === 'permanent') return 'Permanent';
  return normalized;
}

function renderCatalogRecommendationStep() {
  const workflow = state.workflow;
  if (!workflow?.configuredReason) return catalogBaseRenderRecommendationStep();
  $('#workflowTitle').textContent = 'Configured punishment ladder';
  const recommendation = workflow.recommendation;
  replaceChildrenOf($('#workflowBody'),
    element('div',{className:'recommendation-layout'},
      catalogRecommendationCard(recommendation),
      catalogLadderCard(workflow.configuredReason,recommendation)));
  replaceChildrenOf($('#workflowFooter'),
    buttonNode('Back to reasons','button ghost',{catalogReasonBack:''}),
    element('div',{className:'inline'},
      buttonNode('Edit punishment','button secondary',{custom:''}),
      buttonNode('Use starting step','button primary',{useRecommendation:''})));
  $('[data-catalog-reason-back]')?.addEventListener('click',() => {
    workflow.step = 'reason';
    renderWorkflow();
  });
  $('[data-use-recommendation]')?.addEventListener('click',() => useRecommendation(false));
  $('[data-custom]')?.addEventListener('click',() => useRecommendation(true));
}

function catalogRecommendationCard(recommendation) {
  return element('section',{className:'recommendation-card'},
    element('div',{className:'eyebrow',text:'Starting action'}),
    element('div',{className:'recommendation-action',text:recommendation.action}),
    element('div',{className:'recommendation-duration',text:`${recommendation.duration} · ${recommendation.scope}`}),
    element('p',{text:recommendation.explanation}));
}

function catalogLadderCard(reason,recommendation) {
  const card = element('section',{className:'card catalog-ladder-card'},
    element('div',{className:'section-heading'},
      element('div',{},
        element('h3',{text:reason.label}),
        element('p',{text:`${reason.id} · ${catalogFamilyLabel(reason.family)} · severity ${reason.severity}/100`}))),
    summaryList([
      ['Relevant family history',recommendation.relevantCount],
      ['Required rank',catalogFamilyLabel(reason.requiredRank || 'MOD')],
      ['Configured ladder steps',Array.isArray(reason.ladder) ? reason.ladder.length : 0]
    ]));
  const ladder = element('div',{className:'catalog-ladder'});
  for (const step of reason.ladder || []) ladder.append(catalogLadderStep(step));
  card.append(ladder);
  return card;
}

function catalogLadderStep(step) {
  const consequences = (step.consequences || []).map(item => {
    const duration = catalogDuration(item.duration);
    return duration === '—' ? catalogFamilyLabel(item.type) : `${catalogFamilyLabel(item.type)} · ${duration}`;
  });
  return element('div',{className:'catalog-ladder-step'},
    element('strong',{text:`Step ${Number(step.ordinal || 0) + 1} · ${step.label}`}),
    element('span',{text:consequences.join(' + ') || 'No directly executable sanction'}));
}

function catalogWorkflowSteps(step) {
  const order = ['offense','reason','recommendation','options','review'];
  const labels = ['Category','Reason','Ladder','Options','Review'];
  const current = Math.max(0,order.indexOf(step));
  replaceChildrenOf($('#workflowSteps'),labels.map((label,index) => workflowStepNode(label,index,current)));
}

window.openWorkflow = function openCatalogWorkflow() {
  catalogBaseOpenWorkflow();
  if (!punishmentCatalogAvailable() || !state.workflow) return;
  state.workflow.policyFamily = null;
  state.workflow.exactReasonId = null;
  state.workflow.configuredReason = null;
  state.workflow.step = 'offense';
  renderWorkflow();
  $('[data-policy-family]')?.focus();
};

window.renderWorkflow = function renderCatalogWorkflow() {
  if (!state.workflow) return;
  if (state.workflow.step === 'reason' && punishmentCatalogAvailable()) {
    renderCatalogReasonStep();
    return;
  }
  catalogBaseRenderWorkflow();
};

window.renderWorkflowSteps = catalogWorkflowSteps;
window.renderOffenseStep = renderCatalogCategoryStep;
window.renderRecommendationStep = renderCatalogRecommendationStep;
