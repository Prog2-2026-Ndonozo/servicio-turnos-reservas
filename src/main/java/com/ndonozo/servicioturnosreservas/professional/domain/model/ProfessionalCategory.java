package com.ndonozo.servicioturnosreservas.professional.domain.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalCategory {
    private Long id;
    private String name;
    private String description;
    private boolean enabled;

}
