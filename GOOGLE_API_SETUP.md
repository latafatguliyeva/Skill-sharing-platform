# Google Meet & Calendar API Integration

This application now includes integration with Google Calendar and Google Meet APIs to create real calendar events with Google Meet conferences.

## Setup Instructions

### 1. Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the following APIs:
   - Google Calendar API
   - Google Meet API (if available)

### 2. OAuth Credentials

The OAuth credentials are already configured in `src/main/resources/credentials.json`:

```json
{
  "web": {
    "client_id": "705653496230-fq8sk9lgt9kku1n20ij5vjgnmr253qmn.apps.googleusercontent.com",
    "project_id": "strong-matrix-480711-u2",
    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
    "token_uri": "https://oauth2.googleapis.com/token",
    "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
    "client_secret": "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",
    "javascript_origins": [
      "http://localhost:3000",
      "http://localhost:8080"
    ]
  }
}
```

### 3. Enable Required APIs

In Google Cloud Console, ensure these APIs are enabled:
- ✅ **Google Calendar API**
- ✅ **Google Meet API** (if available)

### 4. OAuth Consent Screen

Configure the OAuth consent screen:
- **User Type**: External
- **App name**: Skill Sharing Platform
- **Scopes**: `https://www.googleapis.com/auth/calendar.events`
- **Test users**: Add your Google account email

### 3. API Permissions

The application requests the following OAuth scope:
- `https://www.googleapis.com/auth/calendar.events` - Create and manage calendar events

### 4. First Run

When you first create a **virtual skill-sharing session** (not just visit the website), the application will:

1. Open a browser window for Google OAuth authorization
2. Start a local server on port 8888 to receive the authorization callback
3. Store the OAuth tokens in the `tokens` directory

**Note**: The current implementation uses the desktop OAuth flow. For production deployment, you would need to implement a proper web OAuth flow.

### Testing the Integration

To trigger the Google Meet integration:

1. **Start both servers**:
   ```bash
   # Terminal 1: Frontend
   npm run dev

   # Terminal 2: Backend
   ./mvnw spring-boot:run
   ```

2. **Create user accounts** and add skills in the dashboard

3. **Create a session request**:
   - Go to Browse → Find a teacher
   - Click "Request Session"
   - Select virtual session type
   - Submit the request

4. **Approve the request** (as teacher) - this creates the actual session

5. **Check backend logs** for OAuth flow - a browser should open for Google authorization

### Debugging Fake URLs

If you see URLs like `https://meet.google.com/SS-20251209-1430-ABC123`, the real Google API integration failed. Check for these error patterns in backend logs:

**Common Issues:**

1. **"Resource not found: /credentials.json"**
   - Credentials file missing or not in classpath

2. **"OAuth authentication failed"**
   - Invalid credentials or Google API not enabled
   - Solution: Check Google Cloud Console settings

3. **"Failed to create Google Meet session using Calendar API"**
   - Network issues or API permissions
   - Check internet connection and API quotas

4. **Browser doesn't open for OAuth**
   - Server environment doesn't support desktop OAuth flow
   - Need to implement web OAuth flow for production

### Quick Test for Real Integration

1. Start backend server
2. Check logs immediately when approving a request
3. Look for "Attempting to create Google Meet session using Calendar API"
4. If it fails, check the specific error message
5. If OAuth succeeds, you should see "Successfully created Google Meet session with ID:" followed by a real Google Meet URL

## How It Works

### Calendar Event Creation

When a virtual session is created:

1. The application creates a Google Calendar event with:
   - Title: "Skill Sharing Session: [Skill ID]"
   - Description: Session details with teacher and learner names
   - Start/End times based on the scheduled session time
   - Attendees: Both teacher and learner email addresses

2. Google automatically creates a Google Meet conference for the event

3. The application extracts the Google Meet URL and meeting details

### Fallback Mechanism

If the Google API integration fails for any reason (network issues, authentication problems, etc.), the application automatically falls back to generating mock Google Meet URLs using the enhanced method.

## Configuration

You can customize the Google API integration through `application.properties`:

```properties
# Google API Configuration
google.api.credentials.path=credentials.json
google.api.tokens.directory=tokens
google.api.application.name=Skill Sharing Platform
```

## Testing

To test the integration:

1. Start the backend server
2. Create a new session request as a learner
3. Approve the request as a teacher
4. Check that a calendar event was created in Google Calendar
5. Verify that the meeting URL is a real Google Meet link

## Security Considerations

- OAuth tokens are stored locally in the `tokens` directory
- The application uses "offline" access type to maintain refresh tokens
- For production, implement proper token storage and rotation
- Consider using service accounts for server-to-server authentication

## Troubleshooting

### Common Issues

1. **"Resource not found: /credentials.json"**
   - Ensure the `credentials.json` file exists in `src/main/resources/`

2. **OAuth authorization fails**
   - Check that the redirect URIs in Google Cloud Console match your application URLs
   - Ensure the Calendar API is enabled

3. **Calendar API errors**
   - Verify that the user has granted calendar permissions
   - Check that the user's Google account has calendar access

### Logs

Check the application logs for detailed error messages from the Google API integration.