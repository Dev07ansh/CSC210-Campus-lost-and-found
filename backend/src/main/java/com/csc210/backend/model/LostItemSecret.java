package com.csc210.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/** Private owner-supplied details visible only to authorized staff during claim review. */
@Entity
@Table(name = "lost_item_secret", uniqueConstraints = @UniqueConstraint(columnNames = "lost_item_id"))
public class LostItemSecret {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lost_item_id", nullable = false, unique = true)
    @JsonIgnore
    private LostItem lostItem;
    private String interiorColorOrLining;
    private String uniqueMarks;
    private String contents;

    public LostItemSecret() {}
    public LostItemSecret(String interiorColorOrLining, String uniqueMarks, String contents) {
        this.interiorColorOrLining = interiorColorOrLining;
        this.uniqueMarks = uniqueMarks;
        this.contents = contents;
    }

    public Long getId(){return id;}
    @JsonIgnore public LostItem getLostItem(){return lostItem;}
    public void setLostItem(LostItem value){lostItem=value;}
    @JsonIgnore public String getInteriorColorOrLining(){return interiorColorOrLining;}
    public void setInteriorColorOrLining(String value){interiorColorOrLining=value;}
    @JsonIgnore public String getUniqueMarks(){return uniqueMarks;}
    public void setUniqueMarks(String value){uniqueMarks=value;}
    @JsonIgnore public String getContents(){return contents;}
    public void setContents(String value){contents=value;}
    public boolean isComplete(){return meaningful(interiorColorOrLining) && meaningful(uniqueMarks) && meaningful(contents);}
    private boolean meaningful(String value){return value != null && value.matches(".*[\\p{L}\\p{N}].*");}
}
