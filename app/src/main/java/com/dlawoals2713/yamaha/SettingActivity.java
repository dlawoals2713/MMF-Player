package com.dlawoals2713.yamaha;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dlawoals2713.yamaha.databinding.SettingBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SettingActivity extends AppCompatActivity {
    private SettingBinding binding;
    private SharedPreferences sp;
    private List<String> rateList;
    private final String PREF_KEY = "sr";
    private final String DEFAULT_SR = "22050";

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        binding = SettingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initializeLogic();
    }

    private void initializeLogic() {
        binding.toolbarView.setNavigationButtonOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        sp = getSharedPreferences("setting", MODE_PRIVATE);

        initSpinner();
    }

    private void initSpinner() {
        String[] defaultRates = getResources().getStringArray(R.array.sample_rates);
        rateList = new ArrayList<>();

        Collections.addAll(rateList, defaultRates);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rateList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.seslSpinner.setAdapter(adapter);

        String savedRate = sp.getString(PREF_KEY, DEFAULT_SR);
        int selectedIndex = getIndexByRate(savedRate);
        binding.seslSpinner.setSelection(selectedIndex);

        binding.seslSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = rateList.get(position);
                saveRate(stripHz(selected));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void saveRate(String rate) {
        if (!Objects.equals(rate, sp.getString(PREF_KEY, DEFAULT_SR))) {
            sp.edit().putString(PREF_KEY, rate).apply();
            Toast.makeText(this, getString(R.string.setting_saved_rate), Toast.LENGTH_SHORT).show();
        }
    }

    private int getIndexByRate(String rate) {
        for (int i = 0; i < rateList.size(); i++) {
            if (stripHz(rateList.get(i)).equals(rate)) {
                return i;
            }
        }
        return rateList.size() - 1;
    }

    private String stripHz(String s) {
        return s.replace(" Hz", "").trim();
    }
}
