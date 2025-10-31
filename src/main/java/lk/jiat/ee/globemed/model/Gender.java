package lk.jiat.ee.globemed.model;

public class Gender {
    private int gender_id;
    private String gender_name;

    public Gender(int gender_id, String gender_name) {
        this.gender_id = gender_id;
        this.gender_name = gender_name;
    }

    public int getGender_id() {
        return gender_id;
    }

    public void setGender_id(int gender_id) {
        this.gender_id = gender_id;
    }

    public String getGender_name() {
        return gender_name;
    }

    public void setGender_name(String gender_name) {
        this.gender_name = gender_name;
    }

    @Override
    public String toString() {
        return gender_name;
    }
}
