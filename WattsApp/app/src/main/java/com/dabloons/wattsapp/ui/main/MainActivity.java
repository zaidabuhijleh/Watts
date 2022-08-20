package com.dabloons.wattsapp.ui.main;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Bundle;
import android.view.MenuItem;

import com.dabloons.wattsapp.R;
import com.dabloons.wattsapp.databinding.ActivityMainBinding;
import com.dabloons.wattsapp.ui.BaseActivity;
import com.dabloons.wattsapp.ui.main.fragment.AccountFragment;
import com.dabloons.wattsapp.ui.main.fragment.HomeFragment;
import com.dabloons.wattsapp.ui.main.fragment.TestFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends BaseActivity<ActivityMainBinding> implements NavigationBarView.OnItemSelectedListener{

    private HomeFragment homeFragment;
    private TestFragment testFragment;
    private AccountFragment accountFragment;

    private BottomNavigationView bottomMenu;

    @Override
    protected ActivityMainBinding getViewBinding() {
        return ActivityMainBinding.inflate(getLayoutInflater());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        homeFragment = new HomeFragment();
        testFragment = new TestFragment();
        accountFragment = new AccountFragment();

        bottomMenu = findViewById(R.id.bottom_navigation);

        bottomMenu.setOnItemSelectedListener(this);

        bottomMenu.setSelectedItemId(R.id.page_1);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.page_1:
                getSupportFragmentManager().beginTransaction().replace(R.id.flFragment, homeFragment).commit();
                return true;

            case R.id.page_2:
                getSupportFragmentManager().beginTransaction().replace(R.id.flFragment, testFragment).commit();
                return true;

            case R.id.page_3:
                getSupportFragmentManager().beginTransaction().replace(R.id.flFragment, accountFragment).commit();
                return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}