package es.cineasee.analitica.clients.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private Long id;
    private String email;
    private String fullName;
    private String role;
    private Integer loyaltyPoints;
    private List<String> preferredGenres;
}

