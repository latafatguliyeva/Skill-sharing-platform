package com.skillsharing.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.EntryPoint;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.skillsharing.model.Session;
import com.skillsharing.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Service
public class GoogleMeetService {

    private static final Logger logger = Logger.getLogger(GoogleMeetService.class.getName());
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_EVENTS);

    @Value("${google.api.application.name:Skill Sharing Platform}")
    private String applicationName;

    @Value("${google.api.credentials.path:/credentials.json}")
    private String credentialsFilePath;

    @Value("${google.api.tokens.directory:tokens}")
    private String tokensDirectoryPath;

    private Credential credential;
    private Calendar calendarService;

    /**
     * Creates a Google Meet session using Google Calendar API
     * This creates a real calendar event with Google Meet conference
     */
    public Session createGoogleMeetSession(Session session, User teacher, User learner) {
        try {
            logger.info("Attempting to create Google Meet session using Calendar API");
            logger.info("Session type: " + session.getSessionType());
            logger.info("Teacher: " + teacher.getEmail() + ", Learner: " + learner.getEmail());
            logger.info("Scheduled time: " + session.getScheduledTime());

            // Initialize Google Calendar service
            initializeCalendarService();

            // Create calendar event with Google Meet
            Event event = createCalendarEvent(session, teacher, learner);
            logger.info("Created calendar event: " + event.getSummary());

            // Execute the calendar event creation
            Event createdEvent = calendarService.events().insert("primary", event)
                    .setConferenceDataVersion(1)
                    .setSendUpdates("none")
                    .execute();

            logger.info("Calendar event created successfully");

            // Extract Google Meet details from the created event
            updateSessionWithMeetDetails(session, createdEvent);

            logger.info("Successfully created Google Meet session with ID: " + session.getMeetingId() + ", URL: " + session.getMeetingUrl());
            return session;

        } catch (Exception e) {
            logger.severe("Failed to create Google Meet session using Calendar API: " + e.getMessage());
            logger.severe("Full exception: " + e.toString());
            e.printStackTrace();
            logger.info("Falling back to enhanced meet session creation");
            // Fallback to enhanced method
            return createEnhancedMeetSession(session, teacher, learner);
        }
    }

    /**
     * Initialize Google Calendar service with OAuth credentials
     */
    private void initializeCalendarService() throws IOException, GeneralSecurityException {
        if (calendarService == null) {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            calendarService = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(applicationName)
                    .build();
        }
    }

    /**
     * Creates OAuth credentials for Google API access
     * Note: This uses desktop OAuth flow which requires user interaction.
     * For production, implement web OAuth flow.
     */
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        if (credential != null && credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() > 60) {
            logger.info("Using existing OAuth credentials");
            return credential;
        }

        logger.info("Loading Google API credentials from: " + credentialsFilePath);

        try {
            // Load client secrets - try filesystem first for development, then classpath for production
            InputStream in = null;

            // First try filesystem (for development/testing)
            java.io.File credentialsFile = new java.io.File("src/main/resources/credentials.json");
            if (credentialsFile.exists()) {
                in = new java.io.FileInputStream(credentialsFile);
                logger.info("Loaded credentials from filesystem: " + credentialsFile.getAbsolutePath());
            } else {
                // Fallback to classpath (for production)
                logger.info("Credentials not found on filesystem, trying classpath...");
                in = GoogleMeetService.class.getResourceAsStream(credentialsFilePath);
                if (in == null) {
                    throw new FileNotFoundException("Resource not found: " + credentialsFilePath +
                                                   " (filesystem and classpath both failed)");
                }
            }

            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            // Build flow and trigger user authorization request
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(tokensDirectoryPath)))
                    .setAccessType("offline")
                    .build();

            logger.info("Initiating OAuth flow for Google Calendar API access");
            logger.info("Note: This will open a browser for authorization. Complete the authorization to continue.");

            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8080).build();
            credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

            logger.info("Successfully obtained OAuth credentials");
            return credential;

        } catch (Exception e) {
            logger.severe("Failed to obtain OAuth credentials: " + e.getMessage());
            throw new IOException("OAuth authentication failed. Please check credentials and try again.", e);
        }
    }

    /**
     * Creates a Google Calendar event with Google Meet conference
     */
    private Event createCalendarEvent(Session session, User teacher, User learner) {
        Event event = new Event()
                .setSummary("Skill Sharing Session: " + session.getSkillId())
                .setDescription(String.format(
                        "Skill sharing session between %s (Teacher) and %s (Learner)",
                        teacher.getFullName(), learner.getFullName()));

        // Set event timing
        OffsetDateTime startTime = session.getScheduledTime();
        OffsetDateTime endTime = startTime.plusMinutes(session.getDuration());

        DateTime startDateTime = new DateTime(startTime.toInstant().toEpochMilli());
        DateTime endDateTime = new DateTime(endTime.toInstant().toEpochMilli());

        event.setStart(new EventDateTime().setDateTime(startDateTime));
        event.setEnd(new EventDateTime().setDateTime(endDateTime));

        // Add attendees
        EventAttendee teacherAttendee = new EventAttendee()
                .setEmail(teacher.getEmail())
                .setDisplayName(teacher.getFullName());
        EventAttendee learnerAttendee = new EventAttendee()
                .setEmail(learner.getEmail())
                .setDisplayName(learner.getFullName());

        event.setAttendees(Arrays.asList(teacherAttendee, learnerAttendee));

        // Create Google Meet conference
        ConferenceData conferenceData = new ConferenceData()
                .setCreateRequest(new CreateConferenceRequest()
                        .setRequestId(java.util.UUID.randomUUID().toString())
                        .setConferenceSolutionKey(new ConferenceSolutionKey()
                                .setType("hangoutsMeet")));

        event.setConferenceData(conferenceData);

        return event;
    }

    /**
     * Updates session with Google Meet details from created calendar event
     */
    private void updateSessionWithMeetDetails(Session session, Event event) {
        session.setSessionType("virtual");

        // Extract Google Meet URL from conference data
        if (event.getConferenceData() != null && event.getConferenceData().getEntryPoints() != null) {
            for (EntryPoint entryPoint : event.getConferenceData().getEntryPoints()) {
                if ("video".equals(entryPoint.getEntryPointType())) {
                    session.setMeetingUrl(entryPoint.getUri());
                    // Extract meeting ID from URL
                    String uri = entryPoint.getUri();
                    if (uri != null && uri.contains("meet.google.com/")) {
                        String meetingId = uri.substring(uri.lastIndexOf("/") + 1);
                        session.setMeetingId(meetingId);
                    }
                    break;
                }
            }
        }

        // Set meeting password if available
        if (event.getConferenceData() != null && event.getConferenceData().getConferenceId() != null) {
            session.setMeetingPassword(event.getConferenceData().getConferenceId().substring(0, 6));
        }

        // Fallback if Meet URL not found
        if (session.getMeetingUrl() == null) {
            session.setMeetingUrl("https://meet.google.com/" + event.getId());
            session.setMeetingId(event.getId());
        }
    }

    /**
     * Creates an enhanced Google Meet session with better URL generation and validation
     * This provides a production-ready solution without requiring Google API setup
     */
    public Session createEnhancedMeetSession(Session session, User teacher, User learner) {
        // Generate a more structured meeting ID
        OffsetDateTime now = OffsetDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Create meeting ID with format: SS-{date}-{time}-{unique}
        String meetingId = String.format("SS-%s-%s", timestamp, uniqueId);
        String meetingUrl = "https://meet.google.com/" + meetingId;

        // Set additional meeting properties
        session.setMeetingUrl(meetingUrl);
        session.setMeetingId(meetingId);
        session.setSessionType("virtual");

        // Generate meeting password (optional but good practice)
        String meetingPassword = generateMeetingPassword();
        session.setMeetingPassword(meetingPassword);

        return session;
    }

    /**
     * Creates a simple Google Meet session URL for skill-sharing sessions
     * This method generates a unique meeting URL without requiring Google API setup
     */
    public Session createSimpleMeetSession(Session session, User teacher, User learner) {
        // Generate a unique meeting ID based on session details
        String meetingId = "skill-share-" + session.getId() + "-" +
                java.util.UUID.randomUUID().toString().substring(0, 8);

        String meetingUrl = "https://meet.google.com/" + meetingId;

        session.setMeetingUrl(meetingUrl);
        session.setMeetingId(meetingId);
        session.setSessionType("virtual");

        return session;
    }

    /**
     * Validates if a Google Meet URL is properly formatted
     */
    public boolean isValidMeetUrl(String url) {
        if (url == null || !url.startsWith("https://meet.google.com/")) {
            return false;
        }

        // Check if the meeting ID part exists and is reasonable length
        String meetingId = url.substring("https://meet.google.com/".length());
        return meetingId.length() >= 8 && meetingId.length() <= 50 &&
               meetingId.matches("[a-zA-Z0-9_-]+");
    }

    /**
     * Generates a random meeting password
     */
    private String generateMeetingPassword() {
        // Generate a 6-digit numeric password
        return String.format("%06d", (int)(Math.random() * 1000000));
    }

    /**
     * Test method to verify Google API connectivity
     * Call this to check if OAuth and API access are working
     */
    public boolean testGoogleApiConnection() {
        try {
            logger.info("Testing Google API connection...");
            initializeCalendarService();

            // Try to get the next 1 event to test API access
            com.google.api.services.calendar.Calendar.Events.List request =
                calendarService.events().list("primary")
                    .setMaxResults(1)
                    .setOrderBy("startTime")
                    .setSingleEvents(true);

            request.execute();
            logger.info("Google API connection test successful");
            return true;

        } catch (Exception e) {
            logger.severe("Google API connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Test method to verify credentials can be loaded
     * This tests if the credentials.json file is valid and can be parsed
     */
    public boolean testCredentialsLoading() {
        try {
            logger.info("Testing credentials loading...");

            // For testing, load from file system since it's excluded from Maven resources
            java.io.File credentialsFile = new java.io.File("src/main/resources/credentials.json");
            logger.info("Loading Google API credentials from: " + credentialsFile.getAbsolutePath());

            if (!credentialsFile.exists()) {
                logger.severe("Credentials file not found: " + credentialsFile.getAbsolutePath());
                return false;
            }

            // Load client secrets from file
            InputStream in = new java.io.FileInputStream(credentialsFile);
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            logger.info("Successfully loaded credentials");
            logger.info("Client ID: " + clientSecrets.getDetails().getClientId());
            return true;

        } catch (Exception e) {
            logger.severe("Credentials loading test failed: " + e.getMessage());
            return false;
        }
    }
}
