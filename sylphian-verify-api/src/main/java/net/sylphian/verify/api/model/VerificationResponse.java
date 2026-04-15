package net.sylphian.verify.api.model;
 
import com.google.gson.annotations.SerializedName;
 
public class VerificationResponse {
    @SerializedName("allowed")
    private Boolean allowed;
    private VerificationReason reason;
    private String passcode;
    @SerializedName("forum_user_id")
    private int forumUserId;
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

    public VerificationResponse(boolean allowed, VerificationReason reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public boolean isAllowed() {
        return allowed != null && allowed;
    }

    public Boolean getAllowed() {
        return allowed;
    }

    public void setAllowed(Boolean allowed) {
        this.allowed = allowed;
    }

    public VerificationReason getReason() {
        return reason;
    }

    public String getPasscode() {
        return passcode;
    }

    public int getForumUserId() {
        return forumUserId;
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
