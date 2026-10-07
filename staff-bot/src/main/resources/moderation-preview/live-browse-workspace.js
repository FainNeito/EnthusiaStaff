'use strict';

const browseBaseApplyLiveBootstrap = window.applyLiveBootstrap;
const browseBaseRenderTargetHeader = window.renderTargetHeader;
const browseBaseRenderContextPanel = window.renderContextPanel;
const browseBaseRenderCounts = window.renderCounts;
const browseBaseBindMessageEvents = window.bindMessageEvents;
const browseBaseSimulationRequest = window.simulationRequest;
const browseBaseSessionBoundChannelId = window.sessionBoundChannelId;
const TARGET_ONLY_VIEWS = new Set(['overview', 'history', 'cases', 'notes', 'accounts']);

liveModeration.targetSelected = false;
liveModeration.playerSearchResults = [];
liveModeration.playerSearchBusy = false;
state.activeTargetKey = '';
state.playerPickerPrompted = false;
const PLAYER_PICKER_MIN_SEARCH = 2;

function browseSessionBoundChannelId() {
  const key = state.session?.targetKey;
  const parts = typeof key === 'string' ? key.split(':') : [];
  if (parts[0] === 'channel' && parts.length === 2) return parts[1];
  return browseBaseSessionBoundChannelId();
}

function browseApplyLiveBootstrap(payload) {
  browseBaseApplyLiveBootstrap(payload);
  applyBrowseTargetState(payload);
  renderAll();
  if (!liveModeration.targetSelected && browseSessionSupportsPlayerSwitch() && !state.playerPickerPrompted) {
    state.playerPickerPrompted = true;
    setTimeout(openPlayerPicker,0);
  }
}

function applyBrowseTargetState(payload) {
  const selected = Boolean(payload?.targetSelected && payload?.identity);
  liveModeration.targetSelected = selected;
  state.activeTargetKey = optionalText(payload?.targetKey) || state.session?.targetKey || '';
  if (selected) return;
  setNoPlayerIdentity();
  if (TARGET_ONLY_VIEWS.has(state.view)) state.view = 'messages';
  resetCaseSelections();
}

function setNoPlayerIdentity() {
  Object.assign(identity, {
    displayName:'No player selected', username:'', discordId:'', minecraft:'', minecraftUuid:'', alts:[],
    status:'Channel browse', statusDetail:'Select a player for moderation history and actions',
    avatarUrl:'', linkState:'No player selected', globalName:'', serverName:''
  });
}

function browseRenderTargetHeader() {
  if (liveModeration.targetSelected) {
    browseBaseRenderTargetHeader();
    if (browseSessionSupportsPlayerSwitch()) {
      const actions = $('#targetHeader .target-actions');
      if (actions) actions.insertBefore(
        buttonNode('Change player','button secondary',{openPlayerPicker:''}),
        actions.firstChild
      );
      $('#targetHeader .target-identity')?.append(browseChannelPickerNode());
    }
  } else {
    renderChannelBrowseHeader();
  }
  bindBrowsePickers();
}

function browseSessionSupportsPlayerSwitch() {
  const key = state.session?.targetKey;
  return typeof key === 'string' && key.startsWith('channel:');
}

function renderChannelBrowseHeader() {
  const channel = selectedChannel();
  const identityBlock = element('div',{className:'target-identity'},
    element('div',{className:'identity-line'},element('h1',{id:'targetName',text:'Select a player'})),
    element('div',{className:'target-subline',text:channel
      ? `Browsing #${channel.name} · choose a player to view history and take action`
      : 'Choose a player to view moderation history and actions'}));
  const actions = element('div',{className:'target-actions player-entry-actions'},
    browseChannelPickerNode(),
    buttonNode('Select player','button primary',{openPlayerPicker:''}));
  replaceChildrenOf($('#targetHeader'),
    element('div',{className:'target-avatar',text:'?',attrs:{'aria-hidden':'true'}}),
    identityBlock,
    actions);
}

function browseChannelPickerNode() {
  return browsePickerField('Channel', channelPickerNode());
}

function browsePickerField(label, control) {
  return element('label',{className:'filter-field browse-picker'},element('span',{text:label}),control);
}

function channelPickerNode() {
  return element('select',{id:'workspaceChannelPicker'},
    liveModeration.channels.map((channel) => optionNode(channel.id, `#${channel.name}`, state.channel === channel.id)));
}

function loadedAuthors(selectedId) {
  const byId = new Map();
  for (const message of baseMessages) {
    if (!message.authorId) continue;
    byId.set(message.authorId,{
      id:message.authorId,
      label:`${message.author} · @${message.username}`,
      avatarUrl:message.avatarUrl || '',
      source:'recent'
    });
  }
  if (selectedId && !byId.has(selectedId)) {
    byId.set(selectedId,{
      id:selectedId,
      label:`${identity.displayName} · @${identity.username}`,
      avatarUrl:identity.avatarUrl || '',
      source:'current'
    });
  }
  return [...byId.values()].sort((left,right) => left.label.localeCompare(right.label));
}

function bindBrowsePickers() {
  $('#workspaceChannelPicker')?.addEventListener('change', changeBrowseChannel);
  $$('[data-open-player-picker]').forEach((button) => button.addEventListener('click', openPlayerPicker));
}

function openPlayerPicker() {
  if (!browseSessionSupportsPlayerSwitch()) {
    showToast('Open /moderate without choosing a user to switch between players.',true);
    return;
  }
  $('#playerPickerDialog')?.remove();
  const dialog = ensurePlayerPickerDialog();
  liveModeration.playerSearchResults = [];
  liveModeration.playerSearchBusy = false;
  renderPlayerPickerResults('');
  if (!dialog.open) dialog.showModal();
  $('#playerPickerSearch')?.focus();
}

function ensurePlayerPickerDialog() {
  let dialog = $('#playerPickerDialog');
  if (dialog) return dialog;
  dialog = element('dialog',{id:'playerPickerDialog',className:'player-picker-dialog'},
    element('div',{className:'player-picker-frame'},
      element('header',{className:'dialog-header'},
        element('div',{},
          element('div',{className:'eyebrow',text:'Moderation target'}),
          element('h2',{text:'Select player'})),
        element('button',{type:'button',className:'icon-button',text:'×',
          attrs:{'aria-label':'Close player selector'},dataset:{closePlayerPicker:''}})),
      element('div',{className:'player-picker-body'},
        element('div',{className:'player-search-row'},
          element('input',{id:'playerPickerSearch',type:'search',
            placeholder:'Search Discord name or paste a user ID',
            attrs:{autocomplete:'off','aria-label':'Search players'}}),
          buttonNode('Search history','button secondary',{searchPlayers:''})),
        element('div',{id:'playerPickerStatus',className:'muted small'}),
        element('div',{id:'playerPickerResults',className:'player-picker-results'})),
      element('footer',{className:'dialog-footer player-picker-footer'},
        liveModeration.targetSelected
          ? buttonNode('Clear player','button ghost',{clearPlayer:''})
          : element('span',{className:'muted small',text:'Choose a player to continue'}),
        buttonNode('Close','button secondary',{closePlayerPicker:''}))));
  document.body.append(dialog);
  $('#playerPickerSearch').addEventListener('input', (event) => renderPlayerPickerResults(event.target.value));
  $('#playerPickerSearch').addEventListener('keydown', (event) => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    searchBrowsePlayers();
  });
  $('[data-search-players]').addEventListener('click',searchBrowsePlayers);
  $$('[data-close-player-picker]').forEach((button) => button.addEventListener('click',() => dialog.close()));
  $('[data-clear-player]')?.addEventListener('click',async () => {
    if (await clearBrowsePlayer()) dialog.close();
  });
  return dialog;
}

function playerPickerCandidates(term) {
  const selectedId = liveModeration.targetSelected ? identity.discordId : '';
  const byId = new Map();
  for (const candidate of [...loadedAuthors(selectedId), ...liveModeration.playerSearchResults]) {
    if (candidate?.id) byId.set(candidate.id,candidate);
  }
  const normalized = String(term || '').trim().toLowerCase();
  if (/^[1-9][0-9]{0,19}$/.test(normalized) && !byId.has(normalized)) {
    byId.set(normalized,{id:normalized,label:`Discord user ${normalized}`,avatarUrl:'',source:'id'});
  }
  const candidates = [...byId.values()];
  if (!normalized || /^[1-9][0-9]{0,19}$/.test(normalized)) return candidates;
  return candidates.filter((candidate) =>
    candidate.id.includes(normalized) || candidate.label.toLowerCase().includes(normalized));
}

function renderPlayerPickerResults(term) {
  const container = $('#playerPickerResults');
  if (!container) return;
  const input = String(term || '').trim();
  const candidates = playerPickerCandidates(input);
  const status = $('#playerPickerStatus');
  if (status) {
    status.textContent = liveModeration.playerSearchBusy
      ? 'Searching Discord history…'
      : input.length >= PLAYER_PICKER_MIN_SEARCH
        ? 'Select a result, search older Discord history, or paste an exact Discord user ID.'
        : 'Recent players from loaded Discord messages.';
  }
  if (liveModeration.playerSearchBusy) {
    replaceChildrenOf(container,element('div',{className:'empty-inline',text:'Searching…'}));
    return;
  }
  if (!candidates.length) {
    replaceChildrenOf(container,emptyState('No matching recent players',
      'Search Discord history, switch channels, or paste the exact Discord user ID.'));
    return;
  }
  replaceChildrenOf(container,candidates.map(playerCandidateNode));
  $$('[data-pick-player]').forEach((button) => button.addEventListener('click',choosePlayerFromDialog));
}

function playerCandidateNode(candidate) {
  const current = liveModeration.targetSelected && candidate.id === identity.discordId;
  const avatar = candidate.avatarUrl
    ? playerCandidateImage(candidate)
    : element('div',{className:'player-result-avatar',text:initials(candidate.label),attrs:{'aria-hidden':'true'}});
  return element('button',{type:'button',className:`player-result${current ? ' current' : ''}`,
    dataset:{pickPlayer:candidate.id}},
    avatar,
    element('span',{className:'player-result-copy'},
      element('strong',{text:candidate.label}),
      element('small',{text:`${candidate.id}${current ? ' · Current player' : ''}`})),
    element('span',{className:'player-result-action',text:current ? 'Selected' : 'Open'}));
}

function playerCandidateImage(candidate) {
  const image = document.createElement('img');
  image.className = 'player-result-avatar';
  image.src = candidate.avatarUrl;
  image.alt = '';
  image.referrerPolicy = 'no-referrer';
  return image;
}

async function choosePlayerFromDialog(event) {
  const button = event.currentTarget;
  const userId = button.dataset.pickPlayer;
  button.disabled = true;
  const selected = await selectBrowsePlayer(userId);
  button.disabled = false;
  if (selected) $('#playerPickerDialog')?.close();
}

async function searchBrowsePlayers() {
  const input = $('#playerPickerSearch');
  const term = String(input?.value || '').trim();
  if (/^[1-9][0-9]{0,19}$/.test(term)) {
    liveModeration.playerSearchResults = [];
    renderPlayerPickerResults(term);
    return;
  }
  if (term.length < PLAYER_PICKER_MIN_SEARCH) {
    showToast(`Enter at least ${PLAYER_PICKER_MIN_SEARCH} characters or paste a Discord user ID.`,true);
    input?.focus();
    return;
  }
  liveModeration.playerSearchBusy = true;
  renderPlayerPickerResults(term);
  try {
    const response = await requestDirectModerationRead('/api/messages',{
      method:'POST',
      headers:{Accept:'application/json','Content-Type':'application/json'},
      body:JSON.stringify({author:term,limit:50})
    });
    const page = await readJsonResponse(response);
    if (!response.ok) throw new Error(page.message || 'Player search is unavailable.');
    liveModeration.playerSearchResults = uniquePlayerCandidates(asArray(page.messages).map(window.mapMessage));
  } catch (error) {
    liveModeration.playerSearchResults = [];
    showToast(error.message || 'Player search is temporarily unavailable.',true);
  } finally {
    liveModeration.playerSearchBusy = false;
    renderPlayerPickerResults(term);
  }
}

function uniquePlayerCandidates(messages) {
  const byId = new Map();
  for (const message of messages) {
    if (!message.authorId) continue;
    byId.set(message.authorId,{
      id:message.authorId,
      label:`${message.author} · @${message.username}`,
      avatarUrl:message.avatarUrl || '',
      source:'search'
    });
  }
  return [...byId.values()].sort((left,right) => left.label.localeCompare(right.label));
}

async function changeBrowseChannel(event) {
  state.channel = event.target.value;
  state.contextId = null;
  state.contextReturn = null;
  clearLocalMessageFilters();
  state.channel = event.target.value;
  await loadChannelPage();
}

async function selectBrowsePlayer(userId) {
  if (!/^[1-9][0-9]{0,19}$/.test(userId)) {
    showToast('That Discord user cannot be selected.',true);
    return false;
  }
  const preserved = preserveMessageWorkspace();
  try {
    const payload = await fetchBrowseBootstrap({target:userId,channel:currentBrowseChannel()});
    if (!payload.targetSelected || !payload.identity) throw new Error('Player details were not returned.');
    state.playerPickerPrompted = true;
    applySelectedPlayerPayload(payload,preserved);
    return true;
  } catch (error) {
    showToast(error.message || 'Player details are temporarily unavailable.',true);
    return false;
  }
}

async function clearBrowsePlayer() {
  try {
    const payload = await fetchBrowseBootstrap({browse:true,channel:currentBrowseChannel()});
    resetCaseSelections();
    state.playerPickerPrompted = true;
    browseApplyLiveBootstrap(payload);
    state.view = 'messages';
    renderAll();
    return true;
  } catch (error) {
    showToast(error.message || 'Channel moderation view is temporarily unavailable.',true);
    return false;
  }
}

async function fetchBrowseBootstrap(body) {
  const response = await requestDirectModerationRead('/api/bootstrap',{
    method:'POST',headers:{Accept:'application/json','Content-Type':'application/json'},body:JSON.stringify(body)
  });
  const payload = await readJsonResponse(response);
  if (!response.ok) throw new Error(payload.message || 'Moderation data unavailable');
  return payload;
}

function preserveMessageWorkspace() {
  return {
    messages:baseMessages.slice(), channel:state.channel,
    olderCursor:liveModeration.olderCursor, newerCursor:liveModeration.newerCursor,
    warning:liveModeration.warning
  };
}

function applySelectedPlayerPayload(payload,preserved) {
  resetCaseSelections();
  liveModeration.bootstrap = payload;
  applyLiveCollections(payload);
  applyIdentity(payload.identity);
  applyRestrictionTargets();
  state.history = asArray(payload.history).map(mapHistoryRow);
  liveModeration.targetSelected = true;
  state.activeTargetKey = payload.targetKey;
  baseMessages.splice(0,baseMessages.length,...preserved.messages);
  state.channel = preserved.channel;
  liveModeration.olderCursor = preserved.olderCursor;
  liveModeration.newerCursor = preserved.newerCursor;
  liveModeration.warning = preserved.warning;
  renderAll();
}

function resetCaseSelections() {
  state.selected.clear();
  state.evidence.clear();
  state.violating.clear();
  state.deleting.clear();
  state.anchor = null;
  state.contextId = null;
  state.contextReturn = null;
  state.workflow = null;
  state.evidenceRevision++;
  if ($('#punishmentDialog')?.open) $('#punishmentDialog').close();
}

function currentBrowseChannel() {
  if (state.channel && state.channel !== 'all') return state.channel;
  return browseSessionBoundChannelId() || liveModeration.channels[0]?.id || '';
}

function selectedChannel() {
  return liveModeration.channels.find((channel) => channel.id === currentBrowseChannel());
}

function browseRenderContextPanel() {
  if (liveModeration.targetSelected) {
    browseBaseRenderContextPanel();
    return;
  }
  const channel = selectedChannel();
  replaceChildrenOf($('#contextPanel'),
    contextSection('Channel',null,channel ? `#${channel.name}` : 'Unavailable','Switch channels from the player header.'),
    contextSection('Messages loaded',null,String(baseMessages.length),'Select any message to build evidence.'),
    contextSection('Player',null,'None selected','Choose an author from the Player picker or a message menu to view player-specific records.'));
}

function browseRenderCounts() {
  browseBaseRenderCounts();
  updateTargetNavigation();
  if (liveModeration.targetSelected) return;
  for (const key of TARGET_ONLY_VIEWS) {
    const target = $(`[data-count="${key}"]`);
    if (target) target.textContent = '—';
  }
}

function updateTargetNavigation() {
  $$('.nav-item').forEach((button) => {
    const targetOnly = TARGET_ONLY_VIEWS.has(button.dataset.view);
    button.disabled = targetOnly && !liveModeration.targetSelected;
    if (button.disabled) button.title = 'Select a player first';
    else button.removeAttribute('title');
  });
}

function browseMessageNode(message) {
  const selected = state.selected.has(message.id);
  const focused = state.contextId === message.id;
  const classes = ['message-row',selected ? 'selected':'',message.deleted ? 'deleted':'',focused ? 'context-focus':'']
    .filter(Boolean).join(' ');
  const checkbox = element('input',{type:'checkbox',className:'message-select',checked:selected,
    attrs:{'aria-label':`Select message from ${message.author} at ${formatExact(message.time)}`}});
  return element('article',{className:classes,dataset:{messageId:message.id},attrs:{
    tabindex:'0','aria-label':`${selected ? 'Selected' : 'Not selected'} message from ${message.author} at ${formatExact(message.time)}. Press Space to toggle selection.`
  }},element('div',{},checkbox),messageAvatarNode(message),polishedMessageBodyNode(message),browseMessageActionsNode(message));
}

function browseMessageActionsNode(message) {
  const menu = hardenedMessageActionsNode(message);
  if (!message.authorId) return menu;
  const items = menu.querySelector('.message-action-menu');
  const viewPlayer = element('button',{type:'button',className:'message-action-item',text:'View player',
    dataset:{selectPlayer:message.authorId},attrs:{role:'menuitem','aria-label':`View moderation details for ${message.author}`}});
  if (items) items.insertBefore(viewPlayer, items.firstChild);
  return menu;
}

function browseBindMessageEvents() {
  browseBaseBindMessageEvents();
  $$('[data-select-player]').forEach((button) => button.addEventListener('click',async (event) => {
    closeContainingMenu(event.currentTarget);
    await selectBrowsePlayer(event.currentTarget.dataset.selectPlayer);
  }));
}

async function fetchBrowseContextAround(channelId,messageId) {
  const response = await requestDirectModerationRead('/api/messages',{
    method:'POST',
    headers:{Accept:'application/json','Content-Type':'application/json'},
    body:JSON.stringify({channel:channelId,around:messageId,limit:LIVE_MESSAGE_PAGE_LIMIT})
  });
  const page = await readJsonResponse(response);
  if (!response.ok) throw new Error(page.message || 'Discord context unavailable');
  return asArray(page.messages).map(window.mapMessage);
}

async function browseShowMessageContext(id) {
  const trigger = baseMessages.find((message) => message.id === id);
  if (!trigger) {
    showToast('That message is no longer in the loaded view.',true);
    return;
  }
  const previous = rememberMessageView();
  showToast('Loading surrounding conversation…');
  try {
    const around = await fetchBrowseContextAround(trigger.channelId,id);
    const context = surroundingConversation(trigger,around);
    showContextWorkspace(trigger,context,previous);
  } catch (error) {
    showToast(error.message || 'Discord context is temporarily unavailable.',true);
  }
}

function surroundingConversation(trigger,around) {
  const byId = new Map([[trigger.id,trigger]]);
  for (const message of around) {
    if (message.channelId === trigger.channelId) byId.set(message.id,message);
  }
  return [...byId.values()].sort((left,right) => new Date(right.time) - new Date(left.time));
}

function showContextWorkspace(trigger,context,previous) {
  state.contextReturn = previous;
  baseMessages.splice(0,baseMessages.length,...context);
  state.search = '';
  state.author = '';
  state.channel = trigger.channelId;
  state.date = 'all';
  state.dateFrom = '';
  state.dateTo = '';
  state.remoteSearchActive = false;
  state.remoteSearchCriteria = null;
  state.selectedOnly = false;
  state.contextId = trigger.id;
  liveModeration.olderCursor = null;
  liveModeration.newerCursor = null;
  renderAll();
}

function browseContextAlertNode() {
  return element('div',{className:'alert info'},
    element('strong',{text:'Conversation context'}),
    element('span',{text:'Showing up to 50 surrounding messages from this channel, including messages from every author. The selected message is highlighted.'}),
    buttonNode('Exit context','text-button',{exitContext:''}));
}

function browseMessageCoverageNode() {
  const range = loadedMessageRange();
  const coverage = state.contextId ? 'Up to 50 surrounding messages in this channel' : 'Partial until Discord history is fully paged';
  const detail = state.contextId
    ? 'Show context uses Discord’s around-message history so the conversation includes nearby messages from all authors, not only the selected player.'
    : messageCoverageExplanation();
  return element('section',{className:'card coverage-card'},sectionHeading('Search coverage'),
    summaryList([['Messages loaded',baseMessages.length],['Loaded date range',range],['Filters search','Loaded messages only'],['Coverage',coverage]]),
    element('p',{className:'muted small',text:detail}));
}

function browseRenderSelectionBar() {
  hardenedRenderSelectionBar();
  if (state.selected.size !== 1) return;
  const actions = $('#selectionBar .selection-actions');
  if (!actions) return;
  const button = buttonNode('Show context','button secondary',{selectedContext:''});
  actions.insertBefore(button, actions.firstChild);
  button.addEventListener('click',() => browseShowMessageContext([...state.selected][0]));
}

function browseSimulationRequest(session) {
  const request = browseBaseSimulationRequest(session);
  if (!liveModeration.targetSelected || !state.activeTargetKey) return request;
  const payload = JSON.parse(request.body);
  payload.target = state.activeTargetKey;
  return {...request,body:JSON.stringify(payload)};
}

window.sessionBoundChannelId = browseSessionBoundChannelId;
window.applyLiveBootstrap = browseApplyLiveBootstrap;
window.renderTargetHeader = browseRenderTargetHeader;
window.renderContextPanel = browseRenderContextPanel;
window.renderCounts = browseRenderCounts;
window.messageNode = browseMessageNode;
window.bindMessageEvents = browseBindMessageEvents;
window.showTwoMinuteContext = browseShowMessageContext;
window.contextAlertNode = browseContextAlertNode;
window.messageCoverageNode = browseMessageCoverageNode;
window.renderSelectionBar = browseRenderSelectionBar;
window.simulationRequest = browseSimulationRequest;

const browseReplyBaseReadJsonResponse = window.readJsonResponse;
const browseReplyBaseMessageNode = window.messageNode;
const browseReplyBaseBindMessageEvents = window.bindMessageEvents;
const browseReplyPreviews = new Map();

async function browseReplyReadJsonResponse(response) {
  const payload = await browseReplyBaseReadJsonResponse(response);
  rememberBrowseReplyPayload(payload);
  return payload;
}

function rememberBrowseReplyPayload(payload) {
  const page = Array.isArray(payload?.messages) ? payload : payload?.messages;
  if (!page || !Array.isArray(page.messages)) return;
  for (const message of page.messages) rememberBrowseReplyPreview(message);
}

function rememberBrowseReplyPreview(message) {
  if (!message?.id) return;
  if (!message.replyPreview) {
    browseReplyPreviews.delete(message.id);
    return;
  }
  const author = message.replyPreview.author ?? {};
  browseReplyPreviews.set(message.id, {
    id:String(message.replyPreview.messageId || message.replyToMessageId || ''),
    author:browseReplyAuthor(author),
    text:typeof message.replyPreview.content === 'string' ? message.replyPreview.content : ''
  });
}

function browseReplyAuthor(author) {
  for (const value of [author.displayName, author.serverName, author.globalName, author.username]) {
    if (typeof value === 'string' && value.length > 0) return value;
  }
  return 'Unknown author';
}

function browseReplyMessageNode(message) {
  const row = browseReplyBaseMessageNode(message);
  decorateBrowseReply(row,message);
  decorateBrowseAuthorTargets(row,message);
  return row;
}

function decorateBrowseReply(row,message) {
  if (!message.replyTo) return;
  const reference = row.querySelector('.reply-reference');
  if (!reference) return;
  const preview = browseReplyPreviewFor(message);
  reference.replaceChildren(...browseReplyChildren(preview));
  reference.classList.add('reply-preview');
  reference.dataset.replyJump = message.replyTo;
  reference.dataset.replyChannel = message.channelId;
  reference.setAttribute('role','button');
  reference.setAttribute('tabindex','0');
  reference.setAttribute('aria-label',preview ? `Open replied-to message from ${preview.author}` : 'Open replied-to message');
  reference.setAttribute('title','Open replied-to message');
}

function browseReplyPreviewFor(message) {
  const remembered = browseReplyPreviews.get(message.id);
  if (remembered) return remembered;
  const loaded = baseMessages.find((candidate) => candidate.id === message.replyTo);
  return loaded ? {id:loaded.id,author:loaded.author,text:loaded.text} : null;
}

function browseReplyChildren(preview) {
  if (!preview) return [element('span',{className:'reply-preview-text',text:'Referenced message'})];
  const text = preview.text || 'Attachment or text unavailable';
  return [
    element('strong',{className:'reply-preview-author',text:`${preview.author} — `}),
    element('span',{className:'reply-preview-text',text})
  ];
}

function decorateBrowseAuthorTargets(row,message) {
  if (liveModeration.targetSelected || !message.authorId) return;
  const targets = [row.querySelector('.message-avatar'),row.querySelector('.message-author-name')].filter(Boolean);
  for (const target of targets) {
    target.dataset.doubleSelectPlayer = message.authorId;
    target.setAttribute('role','button');
    target.setAttribute('tabindex','0');
    target.setAttribute('title','Double-click to select this player');
    target.style.cursor = 'pointer';
    if (target.getAttribute('aria-hidden') === 'true') target.removeAttribute('aria-hidden');
  }
}

function browseReplyBindMessageEvents() {
  browseReplyBaseBindMessageEvents();
  $$('[data-reply-jump]').forEach(bindBrowseReplyTarget);
  $$('[data-double-select-player]').forEach(bindBrowseAuthorTarget);
}

function bindBrowseReplyTarget(target) {
  target.addEventListener('click',handleBrowseReplyClick);
  target.addEventListener('keydown',handleBrowseReplyKey);
}

function handleBrowseReplyClick(event) {
  event.preventDefault();
  event.stopPropagation();
  void jumpToBrowseReply(event.currentTarget);
}

function handleBrowseReplyKey(event) {
  if (!['Enter',' '].includes(event.key)) return;
  event.preventDefault();
  event.stopPropagation();
  void jumpToBrowseReply(event.currentTarget);
}

function bindBrowseAuthorTarget(target) {
  target.addEventListener('click',(event) => event.stopPropagation());
  target.addEventListener('dblclick',handleBrowseAuthorDoubleClick);
  target.addEventListener('keydown',handleBrowseAuthorKey);
}

function handleBrowseAuthorDoubleClick(event) {
  event.preventDefault();
  event.stopPropagation();
  void selectBrowseAuthorTarget(event.currentTarget);
}

function handleBrowseAuthorKey(event) {
  if (event.key !== 'Enter') return;
  event.preventDefault();
  event.stopPropagation();
  void selectBrowseAuthorTarget(event.currentTarget);
}

async function selectBrowseAuthorTarget(target) {
  if (liveModeration.targetSelected) return;
  const userId = target.dataset.doubleSelectPlayer;
  if (userId) await selectBrowsePlayer(userId);
}

async function jumpToBrowseReply(target) {
  const messageId = target.dataset.replyJump;
  if (scrollToBrowseMessage(messageId)) return;
  const channelId = target.dataset.replyChannel;
  if (!messageId || !channelId) return;
  showToast('Loading replied-to message…');
  try {
    const around = await fetchBrowseContextAround(channelId,messageId);
    const trigger = around.find((message) => message.id === messageId);
    if (!trigger) throw new Error('The replied-to message was not returned by Discord.');
    const context = surroundingConversation(trigger,around);
    showContextWorkspace(trigger,context,rememberMessageView());
    scrollToBrowseMessage(messageId);
  } catch (error) {
    showToast(error.message || 'The replied-to message is temporarily unavailable.',true);
  }
}

function scrollToBrowseMessage(messageId) {
  const row = [...document.querySelectorAll('.message-row[data-message-id]')]
    .find((candidate) => candidate.dataset.messageId === messageId);
  if (!row) return false;
  row.scrollIntoView({behavior:'smooth',block:'center'});
  row.focus({preventScroll:true});
  return true;
}

window.readJsonResponse = browseReplyReadJsonResponse;
window.messageNode = browseReplyMessageNode;
window.bindMessageEvents = browseReplyBindMessageEvents;
