package pl.edyta.springbootsecurityjwt.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.edyta.springbootsecurityjwt.models.ERole;
import pl.edyta.springbootsecurityjwt.models.Role;
import pl.edyta.springbootsecurityjwt.models.User;
import pl.edyta.springbootsecurityjwt.payload.request.LoginRequest;
import pl.edyta.springbootsecurityjwt.payload.request.SignupRequest;
import pl.edyta.springbootsecurityjwt.payload.response.JwtResponse;
import pl.edyta.springbootsecurityjwt.payload.response.MessageResponse;
import pl.edyta.springbootsecurityjwt.repo.RoleRepository;
import pl.edyta.springbootsecurityjwt.repo.UserRepository;
import pl.edyta.springbootsecurityjwt.security.jwt.JwtUtils;
import pl.edyta.springbootsecurityjwt.security.services.UserDetailsImpl;

import javax.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(value = "*", maxAge = 3600)
@EnableTransactionManagement
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest){

        Authentication authentication= authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(),loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt=jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails=(UserDetailsImpl) authentication.getPrincipal();

        List <String> roles= userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());


        return ResponseEntity.ok(new JwtResponse(
                jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                roles));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest){
        if(userRepository.existsByUsername(signupRequest.getUsername())){
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }

        if(userRepository.existsByEmail(signupRequest.getEmail())){
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }


        //Create new user's account
        User user = new User(
                signupRequest.getUsername(),
                signupRequest.getEmail(),
                passwordEncoder.encode(signupRequest.getPassword()));

        Set <String> strRoles= signupRequest.getRoles();
        Set<Role> roles=new HashSet<>();

        if(strRoles==null){
            Role userRole=roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(()->new RuntimeException("Role is not found"));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                switch (role){
                    case "admin":
                        Role adminRole= roleRepository.findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(()->new RuntimeException("Role is not found"));
                        roles.add(adminRole);
                        break;
                    case "mod":
                        Role modRole=roleRepository.findByName(ERole.ROLE_MODERATOR)
                                .orElseThrow(()->new RuntimeException("Role is not found"));
                        roles.add(modRole);
                        break;
                    default:
                        Role userRole=roleRepository.findByName(ERole.ROLE_USER)
                                .orElseThrow(()->new RuntimeException("Role is not found"));
                        roles.add(userRole);
                }
            });
        }
        user.setRoles(roles);
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));

    }


    @Transactional
    @DeleteMapping("/delete/{username}")
    public void deleteUserByUsername(@PathVariable String username){
        userRepository.deleteAllByUsername(username);
    }

}
