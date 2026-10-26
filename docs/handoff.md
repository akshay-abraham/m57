# M57 → Lyra handoff

M57 opens its configured HTTPS destination with `m57_source=<service>` and
`m57_intent=new`. Source keys identify a service family, not the hidden model
selected inside another app. No prompt, account identity or chat text is sent.
Custom/unknown AI apps use `other`. The APK contains no provider model IDs.

Lyra's companion `/api/m57/handoff?source=<service>` endpoint authenticates the
current user, selects only registered models with configured provider keys,
then applies an optional school hint. The current registry determines family
matches and fallback; a similar service does not promise an identical model.

On the Lyra server, optional `LYRA_M57_SCHOOL_MODEL_HINTS` is a JSON object keyed
by the trusted profile's school name, then service key, then current model ID:

```json
{"Example School":{"gemini":"<an ID from the current registry>"}}
```

This configuration only suggests a selection; it does not create school/model
access permissions. Unknown, retired or unavailable model IDs fall back to a
configured family/default model. URL parameters cannot supply the school or
grant model access. Deployment credentials stay on the server.

Suggestions apply only to `/chat` when creating a new conversation. An existing
chat keeps its model, and a manual model choice or first send wins over a late
suggestion. A validated service key may survive sign-in in sessionStorage for
ten minutes, then is consumed/cleared. No personal data is stored with it.
Failed suggestions leave the current selection available and show a notice.

## Installed PWA routing

Discovery checks HTTPS handlers and launcher activities for Chromium WebAPK
packages. Metadata start URL and scope must match the configured destination's
HTTPS origin and path boundary. An explicit saved package is used only if it
passes those checks. Missing URL filters can fall back to a verified WebAPK's
launcher; Chrome and the system HTTPS handler follow. Generated WebAPK IDs are
never hardcoded. Plain shortcuts and PWAs installed by other browsers can fall
back to a browser. Test standalone launch and query preservation on the phone.

## Source repository boundary

The administrator can restrict the `akshay-abraham/m57` path on github.com and
raw.githubusercontent.com in supported browser address fields. This is a URL
path rule, separate from family-domain lists. Other GitHub repositories remain
available. A protection pause or authorised 60-second maintenance window permits
review; authenticated About/setup source actions grant that window. The rule
does not restrict the license, remove public source access or cover all mirrors,
clients, URL-hiding modes or encoded aliases.

The protocol and accompanying Next.js implementation were prepared in the main
Lyra repository. They must be deployed there; publishing this Android repository
does not deploy Lyra. A Bubblewrap/native Lyra wrapper remains a future idea.
