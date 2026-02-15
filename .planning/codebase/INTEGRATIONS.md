# Integrations

## External Services

### News Data
- **Service**: NewsAPI (newsapi.org)
- **Purpose**: Fetching live headlines and searching for articles.
- **Integration**: via Retrofit (`com.squareup.retrofit2`)
- **Credentials**: `NEWS_API_KEY` in `secrets.properties` (Environment variable injection in CI/CD recommended).
- **Rate Limits**: 429 extraction handled with exponential backoff.

### Authentication & Cloud
- **Service**: Firebase Authentication
- **Purpose**: User identity management.
- **Library**: `com.google.firebase:firebase-auth-ktx`
- **Config**: `google-services.json` (Required in `app/`).

- **Service**: Google Sign-In
- **Purpose**: Federated login.
- **Library**: `com.google.android.gms:play-services-auth`

- **Service**: Google Drive API
- **Purpose**: User data backup (Privacy-first storage).
- **Library**: `com.google.apis:google-api-services-drive`

## Internal/Local Integrations
- **ML Model**: Bundled `tflite` model in assets.
