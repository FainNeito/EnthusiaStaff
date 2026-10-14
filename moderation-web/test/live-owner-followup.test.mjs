import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const RECORD = new URL('../../staff-bot/src/main/resources/moderation-preview/live-record-usability.js', import.meta.url);
const SHELL = new URL('../../staff-bot/src/main/resources/moderation-preview/live-shell-usability.js', import.meta.url);
const ENHANCEMENTS = new URL('../../staff-bot/src/main/resources/moderation-preview/live-enhancements.js', import.meta.url);
const BROWSE = new URL('../../staff-bot/src/main/resources/moderation-preview/live-browse-workspace.js', import.meta.url);

test('message context loads an around-message neighborhood from every author', async () => {
  const source = await readFile(BROWSE, 'utf8');
  const start = source.indexOf('async function browseShowMessageContext');
  const end = source.indexOf('function browseContextAlertNode', start);
  const contextCode = source.slice(start, end);

  assert.match(contextCode, /fetchBrowseContextAround\(trigger\.channelId,id\)/);
  assert.match(contextCode, /message\.channelId === trigger\.channelId/);
  assert.doesNotMatch(contextCode, /CONTEXT_WINDOW_MS|120_000|boundedTimeContext/);
  assert.match(source, /up to 50 surrounding messages from this channel/);
  assert.match(source, /including messages from every author/);
});

test('channel browse keeps no player selected until staff chooses an author', async () => {
  const source = await readFile(BROWSE, 'utf8');

  assert.match(source, /No player selected/);
  assert.match(source, /workspaceChannelPicker/);
  assert.match(source, /playerPickerDialog/);
  assert.match(source, /data-open-player-picker|openPlayerPicker/);
  assert.match(source, /data-select-player|selectPlayer/);
  assert.match(source, /fetchBrowseBootstrap\(\{browse:true,channel:currentBrowseChannel\(\)\}\)/);
  assert.match(source, /fetchBrowseBootstrap\(\{target:userId,channel:currentBrowseChannel\(\)\}\)/);
  assert.match(source, /TARGET_ONLY_VIEWS/);
  assert.match(source, /state\.activeTargetKey/);
});

test('message paging and initial session loading avoid unnecessary serial and full-shell work', async () => {
  const source = await readFile(RECORD, 'utf8');

  assert.match(source, /const LIVE_MESSAGE_PAGE_LIMIT = '50'/);
  assert.match(source, /Promise\.all\(\[sessionRequest, bootstrapRequest\]\)/);
  assert.match(source, /renderWorkspace\(\);\r?\n    renderCounts\(\);/);
  assert.match(source, /Loading \$\{direction\}…/);
});

test('remote search paging uses submitted criteria instead of edited unsubmitted inputs', async () => {
  const source = await readFile(RECORD, 'utf8');
  const start = source.indexOf('function currentMessageRequestParams');
  const end = source.indexOf('async function fasterLoadMessageRequest', start);
  assert.ok(start >= 0 && end > start);

  const context = {
    state:{
      channel:'123',
      remoteSearchActive:true,
      remoteSearchCriteria:{text:'submitted phrase',author:'Alice',date:'2026-10-04'},
      search:'edited phrase',
      author:'Bob'
    },
    URLSearchParams,
    Object
  };
  vm.runInNewContext(`const LIVE_MESSAGE_PAGE_LIMIT = '50';
    ${source.slice(start, end)}
    result = Object.fromEntries(currentMessageRequestParams().entries());`, context);

  assert.deepEqual({...context.result}, {
    channel:'123',limit:'50',text:'submitted phrase',author:'Alice',date:'2026-10-04'
  });
});

test('empty remote search reloads the normal page and context preserves the submitted search snapshot', async () => {
  const [shell, enhancements, browse] = await Promise.all([
    readFile(SHELL, 'utf8'), readFile(ENHANCEMENTS, 'utf8'), readFile(BROWSE, 'utf8')
  ]);
  const start = shell.indexOf('async function runDiscordHistorySearch');
  const end = shell.indexOf('function discordHistorySearchParams', start);
  const emptyBranch = shell.slice(start, end);

  assert.match(emptyBranch, /state\.remoteSearchActive = false/);
  assert.match(emptyBranch, /state\.remoteSearchCriteria = null/);
  assert.match(emptyBranch, /await loadChannelPage\(\)/);
  assert.match(shell, /state\.remoteSearchCriteria = submittedHistorySearchCriteria\(params\)/);
  assert.match(enhancements, /remoteSearchCriteria:state\.remoteSearchCriteria \? \{\.\.\.state\.remoteSearchCriteria\} : null/);
  assert.match(enhancements, /state\.remoteSearchCriteria = previous\.remoteSearchCriteria/);
  assert.match(browse, /state\.remoteSearchCriteria = null/);
});

test('custom punishment duration is constrained to number and unit dropdown values', async () => {
  const source = await readFile(RECORD, 'utf8');
  const start = source.indexOf('const DURATION_UNITS');
  const end = source.indexOf('function actionHasDuration', start);
  assert.ok(start >= 0 && end > start);

  const context = {window:{}};
  vm.runInNewContext(`${source.slice(start, end)}; result = [
    normalizePunishmentDuration('60 days'),
    normalizePunishmentDuration('12 hours'),
    normalizePunishmentDuration('1 month'),
    normalizePunishmentDuration('permanent'),
    normalizePunishmentDuration('0 days'),
    normalizePunishmentDuration('tomorrow')
  ];`, context);

  assert.deepEqual(Array.from(context.result), [
    '60 days', '12 hours', '1 month', 'Permanent', null, null
  ]);
  assert.match(source, /customDurationAmount/);
  assert.match(source, /customDurationUnit/);
  assert.match(source, /minutes','hours','days','months','permanent/);
  assert.match(source, /workflowDurationReady/);
});
