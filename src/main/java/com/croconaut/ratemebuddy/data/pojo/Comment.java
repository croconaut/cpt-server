package com.croconaut.ratemebuddy.data.pojo;

import java.io.Serializable;

public class Comment implements Serializable, Comparable<Comment> {
    private static final long serialVersionUID = 2L;

    private final String comment;
    private final String statusId;
    private final String profileId;
    private final String profileName;

    private String crocoId;
    private boolean seen;
    private long timeStamp;

    public Comment(Builder builder) {
        this.profileName = builder.profileName;
        this.timeStamp = builder.timeStamp;
        this.statusId = builder.statusId;
        this.comment = builder.comment;
        this.crocoId = builder.crocoId;
        this.seen = builder.seen;
        this.profileId = builder.profileId;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Comment)) return false;

        Comment that = (Comment) o;

        return that.getTimeStamp() == this.timeStamp;
    }

    @Override
    public int hashCode() {
        return (int) (getTimeStamp() ^ (getTimeStamp() >>> 32));
    }


    @Override
    public String toString() {
        return "Comment{" +
                "timeStamp=" + timeStamp +
                ", comment='" + comment + '\'' +
                ", statusId='" + statusId + '\'' +
                ", profileId='" + profileId + '\'' +
                ", crocoId='" + crocoId + '\'' +
                ", seen=" + seen +
                '}';
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getComment() {
        return comment;
    }

    public String getStatusId() {
        return statusId;
    }

    public String getCrocoId() {
        return crocoId;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public boolean isSeen() {
        return seen;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }

    public void setCrocoId(String crocoId) {
        this.crocoId = crocoId;
    }

    @Override
    public int compareTo(Comment comment) {
        return Long.valueOf(this.getTimeStamp()).compareTo(comment.timeStamp);
    }

    public static class Builder {
        private final String comment;
        private final String statusId;
        private final String profileId;
        private final String profileName;

        private String crocoId;
        private long timeStamp;
        private boolean seen = false;

        public Builder(long timeStamp, String comment, String statusId, String profileId, String profileName) {
            this.timeStamp = timeStamp;
            this.statusId = statusId;
            this.comment = comment;
            this.profileId = profileId;
            this.profileName = profileName;
        }

        public Builder(Comment comment) {
            this.timeStamp = comment.timeStamp;
            this.comment = comment.comment;
            this.statusId = comment.statusId;
            this.profileId = comment.profileId;
            this.crocoId = comment.crocoId;
            this.seen = comment.seen;
            this.profileName = comment.profileName;
        }

        public Builder seen(boolean seen) {
            this.seen = seen;
            return this;
        }

        public Builder crocoId(String crocoId) {
            this.crocoId = crocoId;
            return this;
        }

        public Builder timestamp(long timeStamp) {
            this.timeStamp = timeStamp;
            return this;
        }

        public Comment build() {
            return new Comment(this);
        }
    }
}
