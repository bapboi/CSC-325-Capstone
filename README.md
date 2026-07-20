# SmartPantry

SmartPantry is a JavaFX app for tracking what's in your pantry, suggesting
recipes based on what you already have, and building a shopping list for what you're
missing. Ingredients can be added manually, by uploading a photo, or by taking a live photo with your
webcam.

## Features

- **Pantry tracking** — add, view, and remove ingredients, each with quantity, unit,
  category, and expiration date.
- **AI ingredient detection** — snap or upload a photo of an ingredient and Gemini
  fills in the name, category, quantity, and unit for you.
- **Live camera capture** — "Take Photo" opens your webcam directly (via OpenCV) when
  one's available, and falls back to a file-upload screen when it isn't.
- **Recipe suggestions** — Gemini suggests recipes based on your current pantry,
  showing what you already have vs. what you're missing for each one.
- **Saved recipes** — heart a recipe to save it to your collection for later.
- **Shopping list** — add individual missing ingredients, or every missing ingredient
  for a recipe at once, straight to your shopping list.

## Tech stack

- **Java 21** / **JavaFX 21.0.2** (UI)
- **Maven** (build)
- **Firebase Authentication** (login) and **Firestore** (data storage)
- **Google Gemini API** (`gemini-2.5-flash-lite`) — ingredient recognition and recipe
  search
- **OpenCV** (`org.openpnp:opencv`) — live webcam capture (wip)
- **Gson** — JSON parsing

## Prerequisites

- **JDK 21** or newer
- **Maven**
- A **Firebase project** with Authentication and Firestore enabled
- A **Gemini API key** ([Google AI Studio](https://aistudio.google.com/api-keys)) with
  billing set up — see the note on model cost below

## Setup

1. **Clone the repo.**

2. **Firebase service account key.** Copy `firebase-key-example.json` to
   `firebase-key.json` (same directory as `pom.xml`) and fill it in with your Firebase
   project's service account credentials (Firebase Console → Project Settings →
   Service Accounts → Generate new private key).

3. **App config.** Copy `app-config-example.json` to `app-config.json` and fill in:

   ```json
   {
     "webApiKey": "your Firebase Web API key",
     "geminiApiKey": "your Gemini API key"
   }
   ```

   - `webApiKey` comes from Firebase Console → Project Settings → General → Web API Key.
   - `geminiApiKey` comes from [Google AI Studio](https://aistudio.google.com/api-keys).
     The app uses `gemini-2.5-flash-lite` specifically for its price/performance — see
     [pricing](https://ai.google.dev/gemini-api/docs/pricing) before swapping models,
     as some alternatives cost several times more per request.

   Both files are gitignored — never commit your real keys.

4. **Build and run:**

   ```bash
   mvn clean javafx:run
   ```
