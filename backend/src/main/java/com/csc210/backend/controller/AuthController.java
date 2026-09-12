package com.csc210.backend.controller;
import com.csc210.backend.model.User;
import com.csc210.backend.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/auth") public class AuthController {private final UserRepository users;private final GoogleIdTokenVerifier verifier;public AuthController(UserRepository users,@Value("${google.client-id}") String clientId){this.users=users;this.verifier=new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),GsonFactory.getDefaultInstance()).setAudience(Collections.singletonList(clientId)).build();}@PostMapping("/google") public ResponseEntity<Response> google(@RequestBody Request request){try{if(request==null||request.credential()==null)return ResponseEntity.badRequest().build();GoogleIdToken token=verifier.verify(request.credential());if(token==null)return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();GoogleIdToken.Payload payload=token.getPayload();if(payload.getEmail()==null||!Boolean.TRUE.equals(payload.getEmailVerified()))return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();String email=payload.getEmail();String name=payload.get("name") instanceof String value&&!value.isBlank()?value:email.substring(0,email.indexOf('@'));User user=users.findByEmail(email).orElseGet(()->users.save(new User(name,email)));return ResponseEntity.ok(new Response(user.getId(),user.getName(),user.getEmail()));}catch(Exception error){return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();}}public record Request(String credential){}public record Response(Long id,String name,String email){}}
