package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.Role;
import com.healthcare.appointment.entity.User;
import lombok.Getter;

import java.util.UUID;

/**
 * Safe representation of a User for API responses.
 * passwordHash is deliberately excluded.
 */
@Getter
public class UserResponse {

    private final UUID id;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final Role role;
    private final boolean active;

    public UserResponse(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.phone = user.getPhone();
        this.role = user.getRole();
        this.active = user.isActive();
    }
}
