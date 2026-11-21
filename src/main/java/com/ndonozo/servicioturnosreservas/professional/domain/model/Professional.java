package com.ndonozo.servicioturnosreservas.professional.domain.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor


public class Professional {
    private Long id;
    private Long categoryId;
    private String firstName;
    private String lastName;
    private boolean enabled;

}
