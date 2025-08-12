package com.boycottpro.users.model;

import com.boycottpro.models.UserBoycotts;
import com.boycottpro.models.UserCauses;

import java.util.List;

public class UpgradeUserForm {

    private List<UserBoycotts> user_boycotts;
    private List<UserCauses> user_causes;

    public UpgradeUserForm() {
    }

    public UpgradeUserForm(List<UserBoycotts> user_boycotts, List<UserCauses> user_causes) {
        this.user_boycotts = user_boycotts;
        this.user_causes = user_causes;
    }

    public List<UserBoycotts> getUser_boycotts() {
        return user_boycotts;
    }

    public void setUser_boycotts(List<UserBoycotts> user_boycotts) {
        this.user_boycotts = user_boycotts;
    }

    public List<UserCauses> getUser_causes() {
        return user_causes;
    }

    public void setUser_causes(List<UserCauses> user_causes) {
        this.user_causes = user_causes;
    }

    @Override
    public String toString() {
        return "UpgradeUserForm{" +
                "user_boycotts=" + user_boycotts +
                ", user_causes=" + user_causes +
                '}';
    }
}
