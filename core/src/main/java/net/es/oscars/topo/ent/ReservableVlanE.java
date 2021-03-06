package net.es.oscars.topo.ent;


import lombok.*;
import net.es.oscars.dto.IntRange;
import net.es.oscars.topo.ent.IntRangeE;
import net.es.oscars.topo.ent.UrnE;

import javax.persistence.*;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class ReservableVlanE {
    @Id
    @GeneratedValue
    private Long id;

    @OneToOne
    private UrnE urn;

    @ElementCollection
    @CollectionTable
    private Set<IntRangeE> vlanRanges;

    public String toString(){
        return super.toString();
    }
}
