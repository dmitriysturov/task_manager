package com.example.task_manager;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.task_manager.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyWindowInsets();

        setSupportActionBar(binding.toolbar);

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_tasks,
                R.id.nav_calendar,
                R.id.nav_archive
        ).build();

        NavController navController = Navigation.findNavController(
                this,
                R.id.nav_host_fragment_content_main
        );

        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        if (NavigationUI.onNavDestinationSelected(item, navController)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void applyWindowInsets() {
        final int appBarPaddingStart = binding.appBarLayout.getPaddingStart();
        final int appBarPaddingTop = binding.appBarLayout.getPaddingTop();
        final int appBarPaddingEnd = binding.appBarLayout.getPaddingEnd();
        final int appBarPaddingBottom = binding.appBarLayout.getPaddingBottom();

        final View navHost = findViewById(R.id.nav_host_fragment_content_main);

        final int contentPaddingStart = navHost.getPaddingStart();
        final int contentPaddingTop = navHost.getPaddingTop();
        final int contentPaddingEnd = navHost.getPaddingEnd();
        final int contentPaddingBottom = navHost.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            binding.appBarLayout.setPaddingRelative(
                    appBarPaddingStart,
                    appBarPaddingTop + systemInsets.top,
                    appBarPaddingEnd,
                    appBarPaddingBottom
            );

            navHost.setPaddingRelative(
                    contentPaddingStart,
                    contentPaddingTop,
                    contentPaddingEnd,
                    contentPaddingBottom + systemInsets.bottom
            );

            return insets;
        });
    }
}
