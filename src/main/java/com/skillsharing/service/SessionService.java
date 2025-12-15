package com.skillsharing.service;

import com.skillsharing.model.Session;
import com.skillsharing.model.User;
import com.skillsharing.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SessionService {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private GoogleMeetService googleMeetService;

    public Session createSession(Session session) {
        System.out.println("Creating session: " + session.getSessionType() + ", teacherId: " + session.getTeacherId() + ", learnerId: " + session.getLearnerId());

        session.setStatus("scheduled");

        if ("virtual".equals(session.getSessionType())) {
            Optional<User> teacherOpt = userService.findById(session.getTeacherId());
            Optional<User> learnerOpt = userService.findById(session.getLearnerId());

            System.out.println("Teacher found: " + teacherOpt.isPresent() + ", Learner found: " + learnerOpt.isPresent());

            if (teacherOpt.isPresent() && learnerOpt.isPresent()) {
                User teacher = teacherOpt.get();
                User learner = learnerOpt.get();

                // Check if both users have Google OAuth tokens
                boolean teacherHasGoogleAuth = teacher.getGoogleAccessToken() != null && !teacher.getGoogleAccessToken().isEmpty();
                boolean learnerHasGoogleAuth = learner.getGoogleAccessToken() != null && !learner.getGoogleAccessToken().isEmpty();

                if (teacherHasGoogleAuth && learnerHasGoogleAuth) {
                    System.out.println("Both users have Google OAuth - creating calendar events");
                    session = googleMeetService.createCalendarEventsForBothUsers(session, teacher, learner);
                } else {
                    System.out.println("Users missing Google OAuth - using fallback meet session");
                    session = googleMeetService.createEnhancedMeetSession(session, teacher, learner);
                }
                System.out.println("GoogleMeetService returned session with URL: " + session.getMeetingUrl());
            } else {
                System.out.println("Users not found, skipping Google Meet integration");
            }
        }

        Session savedSession = sessionRepository.save(session);
        System.out.println("Session saved with ID: " + savedSession.getId());
        return savedSession;
    }
    
    public Optional<Session> findById(Long id) {
        return sessionRepository.findById(id);
    }
    
    public List<Session> getAllSessions() {
        return sessionRepository.findAll();
    }
    
    public List<Session> getSessionsByTeacher(Long teacherId) {
        return sessionRepository.findByTeacherId(teacherId);
    }
    
    public List<Session> getSessionsByLearner(Long learnerId) {
        return sessionRepository.findByLearnerId(learnerId);
    }
    
    public Session updateSessionStatus(Long id, String status) {
        Optional<Session> sessionOpt = sessionRepository.findById(id);
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            session.setStatus(status);
            return sessionRepository.save(session);
        }
        return null;
    }
}
