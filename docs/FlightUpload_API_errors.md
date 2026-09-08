# Flight Upload API - error responses

Reference for `POST /entries/{localPart}/igc` (`SoaringScoringApi.uploadFlight()`).
Supplied directly by the project owner (2026-09-09) - not a copy of a larger
SoaringScoring doc, just this endpoint's error contract. CLAUDE.md previously
referenced a `SoaringScoringUpload_API.txt` for this that was never actually
in the repo; this file is the real thing for the error table specifically.

| HTTP status | Code | Meaning |
| --- | --- | --- |
| 401 | `MISSING_API_KEY` | No `Authorization: Bearer` header was sent. |
| 401 | `INVALID_API_KEY` | The key doesn't match any issued key, or it's been revoked. |
| 403 | `INSUFFICIENT_SCOPE` | The key is valid but lacks the `flights:write` scope. |
| 400 | `INVALID_ADDRESS` | The `:localPart` isn't a valid `{competitionNumber}-{contestKey}` address. |
| 400 | `EMPTY_OR_UNREADABLE_BODY` | No IGC bytes were received - check Content-Type and that the body isn't empty. |
| 404 | `ENTRY_NOT_FOUND` | No contest entry matches that address. |
| 404 | `NO_OFFICIAL_TASK` | No task is marked official yet for this entry's class (and no `taskId` was given). |
| 500 | `INTERNAL` | Server error - try again shortly. |

All mapped to human-readable text in `AppViewModel.describeUploadError()`.
`INVALID_ADDRESS`/`ENTRY_NOT_FOUND` have two message variants depending on
whether the address came from a manually-typed entry address or from a
DustDevil sign-in `localPart` - see that function's doc comment for why
("check the competition number" is wrong advice when the pilot never typed
one).
