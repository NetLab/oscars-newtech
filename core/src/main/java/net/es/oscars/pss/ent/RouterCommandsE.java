package net.es.oscars.pss.ent;

import lombok.*;
import net.es.oscars.dto.pss.cmd.CommandType;

import javax.persistence.*;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouterCommandsE {

    @Id
    @GeneratedValue
    private Long id;

    @NonNull
    private String deviceUrn;

    @NonNull
    private String connectionId;

    @NonNull
    private CommandType type;

    @NonNull
    @Column(length = 65536)
    private String contents;

}
