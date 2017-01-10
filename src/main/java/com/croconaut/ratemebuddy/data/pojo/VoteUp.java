package com.croconaut.ratemebuddy.data.pojo;


import java.io.Serializable;

public class VoteUp implements Serializable {
    private static final long serialVersionUID = 5L;

    private final String profileName;
    private final String statusId;
    private final String profileId;

    private String crocoId;
    private boolean seen;

    private VoteUp(Builder builder) {
        this.profileName = builder.name;
        this.statusId = builder.statusId;
        this.profileId = builder.profileId;
        this.crocoId = builder.crocoId;
        this.seen = builder.seen;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoteUp)) return false;

        VoteUp that = (VoteUp) o;

        if (!getStatusId().equals(that.getStatusId())) return false;
        return getProfileId().equals(that.getProfileId());

    }

    @Override
    public int hashCode() {
        int result = 31 + getStatusId().hashCode();
        result = 31 * result + getProfileId().hashCode();
        return result;
    }

    public void setSeen(boolean seen){
        this.seen = seen;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getStatusId() {
        return statusId;
    }

    public String getProfileId() {
        return profileId;
    }

    public boolean isSeen() {
        return seen;
    }

    public String getCrocoId() {
        return crocoId;
    }

    public static class Builder {
        private final String name;
        private final String statusId;
        private final String profileId;

        private String crocoId;
        private boolean seen = false;

        public Builder(String name, String statusId, String profileId) {
            this.name = name;
            this.statusId = statusId;
            this.profileId = profileId;
        }

        public Builder(VoteUp voteUp) {
            this.name = voteUp.profileName;
            this.statusId = voteUp.statusId;
            this.profileId = voteUp.profileId;
            this.crocoId = voteUp.crocoId;
            this.seen = voteUp.seen;
        }

        public Builder seen(boolean seen) {
            this.seen = seen;
            return this;
        }

        public Builder crocoId(String crocoId) {
            this.crocoId = crocoId;
            return this;
        }

        public VoteUp build() {
            return new VoteUp(this);
        }
    }
}
