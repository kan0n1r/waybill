package com.waybill;

public record User(long id, String email, String role, String fullName, String city, String avatarUrl) {
    public boolean isStaff() {
        return role.equals("courier") || role.equals("admin");
    }
}
