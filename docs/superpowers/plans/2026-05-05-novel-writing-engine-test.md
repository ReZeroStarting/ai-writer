# Novel Writing Engine — Test Plan

> **Goal:** Verify framework engine correctness with unit tests covering all core services (backend) and the framework Pinia store (frontend).

**Scope:** 7 backend test classes + 1 frontend test file. No integration tests (requires running MySQL + Claude API).

---

## Backend Tests (JUnit 5 + Mockito)

### 1. FrameworkServiceTest
- `shouldOpenFramework` — sets project mode to "framework", saves framework
- `shouldRejectDuplicateFramework` — throws if project already has framework
- `shouldRejectAlreadyFrameworkMode` — throws if project.mode already "framework"
- `shouldGetFramework` — returns framework by id
- `shouldRejectUnauthorizedAccess` — throws ForbiddenException for non-owner
- `shouldFinishFramework` — sets status to FINISHED

### 2. ElementServiceTest
- `shouldCreateElement` — saves element with v1, returns response
- `shouldUpdateElementWithNewVersion` — saves element with incremented version
- `shouldDeleteElement` — sets isDeprecated = true
- `shouldListElementsFilteredByType` — filters by type param
- `shouldGetVersionHistory` — returns all versions for element id
- `shouldAiGenerateAndParseJson` — calls ClaudeService, parses JSON array
- `shouldAiGenerateFallbackOnInvalidJson` — saves raw response on parse failure

### 3. ContextBuilderTest
- `shouldBuildFourLayerContext` — returns ContextPackage with elements, recent content, summaries
- `shouldTruncateSystemContextAtMaxChars` — limits to MAX_SYSTEM_CHARS
- `shouldTruncateRecentContentAtMaxChars` — limits to MAX_RECENT_CONTENT_CHARS
- `shouldIncludeUpTo15Summaries` — limits summary count

### 4. ConflictServiceTest
- `shouldResolveConflictByIgnore` — sets status to IGNORED
- `shouldResolveConflictByPatch` — creates Patch, appends PATCH_APPLIED event
- `shouldResolveConflictByRewrite` — sets status to REWRITTEN
- `shouldRejectInvalidAction` — throws on unknown action
- `shouldFindImpactedChapters` — returns chapters with stale element versions
- `shouldDetectStaleChapterConsistency` — returns conflicts when version changed

### 5. ChapterServiceTest
- `shouldCreateChapterWithElementSnapshot` — snapshots all element versions
- `shouldMarkGenerating` — sets status to AI_GENERATING
- `shouldMarkGenerated` — sets status to AI_GENERATED, stores summary
- `shouldGetLatestChapter` — returns max chapterIndex
- `shouldParseSnapshot` — parses JSON to Map<String, Integer>

### 6. EventServiceTest
- `shouldAppendEvent` — saves WritingEvent with json payload
- `shouldListEventsByFramework` — returns ordered list
- `shouldListEventsUpToTimestamp` — filters by timestamp

---

## Frontend Tests (Vitest + jsdom)

### 7. Framework Store Test
- `shouldInitializeWithNullState` — all refs start empty
- `shouldOpenFramework` — calls API, sets framework
- `shouldCreateElement` — prepends to elements array
- `shouldUpdateElement` — replaces element at same id index
- `shouldDeleteElement` — removes from elements array
- `shouldResolveConflict` — updates conflict in array
