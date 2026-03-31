package net.sylphian.verify.api.model;
 
import com.google.gson.annotations.SerializedName;
 
public class VerificationResponse {
    private boolean allowed = true;
    private String reason;
    private String passcode;
    @SerializedName("forum_username")
    private String forumUsername;
    @SerializedName("minecraft_username")
    private String minecraftUsername;
    @SerializedName("link_date")
    private Long linkDate;
    @SerializedName("confirmed_date")
    private Long confirmedDate;

    public VerificationResponse() {
    }

    public VerificationResponse(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
 
    public String getPasscode() {
        return passcode;
    }
 
    public String getForumUsername() {
        return forumUsername;
    }
 
    public String getMinecraftUsername() {
        return minecraftUsername;
    }
 
    public Long getLinkDate() {
        return linkDate;
    }
 
    public Long getConfirmedDate() {
        return confirmedDate;
    }
}
