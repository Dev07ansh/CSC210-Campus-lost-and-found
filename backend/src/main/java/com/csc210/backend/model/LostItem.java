package com.csc210.backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

@Entity
public class LostItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String itemName, category, color, description, location, imageUrl, status;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;
    @OneToOne(mappedBy = "lostItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private LostItemSecret secret;
    private LocalDateTime dateTime;
    public LostItem() {}
    public Long getId(){return id;} public String getItemName(){return itemName;} public void setItemName(String value){itemName=value;}
    public String getCategory(){return category;} public void setCategory(String value){category=value;}
    public String getColor(){return color;} public void setColor(String value){color=value;}
    public String getDescription(){return description;} public void setDescription(String value){description=value;}
    public String getLocation(){return location;} public void setLocation(String value){location=value;}
    public String getImageUrl(){return imageUrl;} public void setImageUrl(String value){imageUrl=value;}
    public String getStatus(){return status;} public void setStatus(String value){status=value;}
    @JsonProperty("verificationAvailable")
    public boolean isVerificationAvailable(){return secret != null && secret.isComplete();}
    public LocalDateTime getDateTime(){return dateTime;} public void setDateTime(LocalDateTime value){dateTime=value;}
    @JsonIgnore public User getReporter(){return reporter;} public void setReporter(User value){reporter=value;}
    @JsonIgnore public LostItemSecret getSecret(){return secret;}
    public void setSecret(LostItemSecret value){secret=value; if(value!=null) value.setLostItem(this);}
}
