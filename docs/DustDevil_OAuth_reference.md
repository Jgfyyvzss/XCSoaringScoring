# DustDevil.cloud sign-in for your app

Reference doc supplied by the SoaringScoring dev (2026-09-05). Kept verbatim
here as the source of truth for implementing DustDevil.cloud sign-in - see
`DEVELOPMENT.md`'s "DustDevil.cloud sign-in" section for our own design
decisions on top of this.

If you're building a native app — an Android app letting a pilot download
waypoint files and upload IGC files is the motivating case — you can let a
pilot sign in with DustDevil.cloud and have SoaringScoring resolve which of
*their own* contest entries you should show, instead of making them type a
competition number and contest key by hand.

## Why this isn't a normal OAuth client

DustDevil.cloud's OAuth server doesn't support PKCE, so a native app can't
run the authorization code flow itself the way it could against an identity
provider built for mobile clients. Instead, SoaringScoring proxies it: your
app sends the pilot's browser through SoaringScoring's own existing,
already-registered DustDevil.cloud OAuth client, and SoaringScoring hands
the result back to your app afterward. No DustDevil.cloud-side registration
or redirect URI is needed from you at all.

### 1. Register a redirect URI

Request an API key at soaringscoring.com/api-access/register as usual, but
also give your app's own custom URI scheme as the app redirect URI — e.g.
`myapp://oauth-callback` — alongside your scopes. An admin reviews and
approves (or edits, or declines) it together with your scopes; it's never
trusted until then. You'll need `contests:read` at minimum to redeem the
hand-off below, plus whichever of `tasks:read`/`flights:write` your app
actually uses.

### 2. Start the sign-in

Open this in a browser or Custom Tab — not a background request, since the
pilot needs to interact with DustDevil.cloud's own sign-in page:

```http
GET https://soaringscoring.com/api/auth/dustdevil/mobile-start?client_key_id={your key's public id}
```

`client_key_id` is your key's public id (shown on your dashboard) — never
the raw secret; nothing sensitive travels in this URL. This 400s immediately
if your key has no approved redirect URI yet, rather than starting a sign-in
that could never complete.

### 3. Let it redirect back to your app

After the pilot signs in, SoaringScoring redirects to your registered
redirect URI:

```
myapp://oauth-callback?code=<short-lived, single-use code>
```

Your app's own manifest/intent-filter (or iOS universal link equivalent)
catches this the same way it would any custom-scheme deep link.

### 4. Redeem the code

```http
POST https://soaringscoring.com/api/v1/public/auth/dustdevil-mobile/exchange
Authorization: Bearer ssk_live_...
Content-Type: application/json

{ "code": "..." }
```

```json
{
  "pilot": { "name": "Jane Smith", "email": "jane@example.com" },
  "entries": [
    {
      "contestId": "6642a1f3e4b0c123456789ab",
      "contestName": "Australian Nationals 2025",
      "contestSlug": "nationals-2025",
      "classId": "6642a2c1e4b0c123456789cc",
      "className": "Open Class",
      "competitionNumber": "OA",
      "localPart": "oa-nationals"
    }
  ]
}
```

`localPart` is ready to use directly with the Task Distribution API and
Flight Upload API — no need to ask the pilot for it. `entries` only includes
contests SoaringScoring has actually imported from DustDevil.cloud where the
pilot has a matching entry; a contest DustDevil.cloud knows about that
hasn't synced here yet just won't appear — not an error.

The code is single-use and short-lived (2 minutes) — redeem it immediately
on redirect. A second redemption, an expired code, or a code redeemed by a
different API key than the one that started the flow all fail the same way
(a plain 404).

### Error responses

| HTTP status | Meaning |
| --- | --- |
| 400 | `client_key_id` missing/invalid, or that key has no approved redirect URI (at start). |
| 401/403 | Missing/invalid API key, or missing `contests:read` scope (at exchange). |
| 404 | Invalid, expired, or already-used code (at exchange). |

Full protocol detail (including why PKCE isn't an option here) lives in
`docs/DUSTDEVIL_OAUTH.md` §6 in the SoaringScoring repository (external to
this project).
