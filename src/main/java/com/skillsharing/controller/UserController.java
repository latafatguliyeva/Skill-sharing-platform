package com.skillsharing.controller;

import com.skillsharing.model.Skill;
import com.skillsharing.model.User;
import com.skillsharing.model.UserDTO;
import com.skillsharing.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> userDTOs = userService.getAllUsers().stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(userDTOs);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userService.findById(id)
                .map(user -> ResponseEntity.ok(new UserDTO(user)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<UserDTO>> searchBySkill(@RequestParam String skill) {
        List<UserDTO> userDTOs = userService.searchBySkill(skill).stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(userDTOs);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User user) {
        User updated = userService.updateUser(id, user);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }
    
    @PostMapping("/{id}/skills/offered")
    public ResponseEntity<?> addOfferedSkill(@PathVariable Long id, @RequestBody Skill skill) {
        User updated = userService.addOfferedSkill(id, skill);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }
    
    @PostMapping("/{id}/skills/wanted")
    public ResponseEntity<?> addWantedSkill(@PathVariable Long id, @RequestBody Skill skill) {
        User updated = userService.addWantedSkill(id, skill);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}/skills/wanted/{skillId}")
    public ResponseEntity<?> removeWantedSkill(@PathVariable Long id, @PathVariable Long skillId) {
        User updated = userService.removeWantedSkill(id, skillId);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}/skills/offered/{skillId}")
    public ResponseEntity<?> removeOfferedSkill(@PathVariable Long id, @PathVariable Long skillId) {
        User updated = userService.removeOfferedSkill(id, skillId);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }
}
