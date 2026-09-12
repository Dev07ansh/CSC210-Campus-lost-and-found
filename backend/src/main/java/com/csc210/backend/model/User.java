package com.csc210.backend.model;

import jakarta.persistence.*;

@Entity
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name, email;
    public User() {} public User(String name,String email){this.name=name;this.email=email;}
    public Long getId(){return id;} public String getName(){return name;} public void setName(String value){name=value;}
    public String getEmail(){return email;} public void setEmail(String value){email=value;}
}
