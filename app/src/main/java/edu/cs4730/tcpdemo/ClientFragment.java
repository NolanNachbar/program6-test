package edu.cs4730.tcpdemo;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

import edu.cs4730.tcpdemo.databinding.FragmentClientBinding;

public class ClientFragment extends Fragment {
    FragmentClientBinding binding;
    Thread myNet;

    // Define static values for hostname and port
    private static final String HOSTNAME = "10.0.2.2";
    private static final int PORT = 3012;

    // Variables to store socket and streams
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ClientFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentClientBinding.inflate(inflater, container, false);

        binding.startBattle.setOnClickListener(v -> {
            doNetwork stuff = new doNetwork();
            myNet = new Thread(stuff);
            myNet.start();


        });

        // Joystick Movement Buttons
        binding.downButton.setOnClickListener(v -> sendMoveCommand("move 0 1"));
        binding.moveLeftButton.setOnClickListener(v -> sendMoveCommand("move -1 0"));
        binding.moveRightButton.setOnClickListener(v -> sendMoveCommand("move 1 0"));
        binding.upButton.setOnClickListener(v -> sendMoveCommand("move 0 -1"));

        // Action Buttons
        binding.fireButton.setOnClickListener(v -> sendFireCommand(90));
        binding.scanButton.setOnClickListener(v -> sendCommand("scan"));
        binding.noopButton.setOnClickListener(v -> sendCommand("noop"));

        // Hunt Mode Toggle
        binding.huntSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startHuntMode();  // Start hunting
            } else {
                stopHuntMode();   // Stop hunting
            }
        });

        return binding.getRoot();
    }

    // General socket command sending method
    private void sendCommand(final String command) {
        new Thread(() -> {
            try {
                // Only send command if the socket and streams are available
                if (out != null && !socket.isClosed()) {
                    out.println(command);  // Send command
                    String response = in.readLine();  // Read server's response
                    updateBattleLog(response);
                } else {
                    updateBattleLog("No active connection");
                }
            } catch (Exception e) {
                updateBattleLog("Error sending command");
            }
        }).start();
    }

    private void sendMoveCommand(String command) {
        sendCommand(command);
    }

    private void sendFireCommand(int angle) {
        sendCommand("fire " + angle);
    }

    private void updateBattleLog(String message) {
        requireActivity().runOnUiThread(() -> binding.logger.append(message + "\n"));
    }

    // Handler to safely update UI from background threads
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            String message = msg.getData().getString("msg");
            if (message != null) {
                binding.logger.append(message);
            }
        }
    };

    // Method to send messages to the UI thread
    private void mkmsg(String str) {
        Message msg = new Message();
        Bundle b = new Bundle();
        b.putString("msg", str);
        msg.setData(b);
        handler.sendMessage(msg);
    }

    // Network connection thread
    class doNetwork implements Runnable {
        public void run() {
            try {
                InetAddress serverAddr = InetAddress.getByName(HOSTNAME);  // Use the constant value
                socket = new Socket(serverAddr, PORT);  // Use the constant value
                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Send a setup message
                out.println("TestBot 0 0 3");

                // Enable buttons once connected
                requireActivity().runOnUiThread(() -> {
                    binding.downButton.setEnabled(true);
                    binding.moveLeftButton.setEnabled(true);
                    binding.moveRightButton.setEnabled(true);
                    binding.upButton.setEnabled(true);
                    binding.fireButton.setEnabled(true);
                    binding.scanButton.setEnabled(true);
                    binding.noopButton.setEnabled(true);
                });

                while (true) {
                    String response = in.readLine();  // Read server response
                    mkmsg("Server response: " + response + "\n");

                    if (response.equals("Game Over")) {
                        break;
                    }
                }

                in.close();
                out.close();
                socket.close();
            } catch (Exception e) {
                mkmsg("Error in connection");
            }
        }
    }

    private void startHuntMode() {
        new Thread(() -> {
            try {
                while (binding.huntSwitch.isChecked()) {
                    // Scan the environment
                    String closestEnemyDirection = findClosestEnemy();

                    if (closestEnemyDirection != null) {
                        // Move and fire strategically
                        sendMoveCommand("move " + closestEnemyDirection);
                        sendFireCommand(calculateFiringAngle(closestEnemyDirection));
                    } else {
                        sendCommand("noop");
                    }

                    // Collect power-ups
                    collectPowerUps();

                    Thread.sleep(1000); // Allow time for server responses
                }
            } catch (InterruptedException e) {
                updateBattleLog("Hunt mode interrupted");
            }
        }).start();
    }

    private int calculateFiringAngle(String enemyDirection) {
        String[] directionParts = enemyDirection.split(" ");
        int x = Integer.parseInt(directionParts[0]);
        int y = Integer.parseInt(directionParts[1]);

        double angle = Math.toDegrees(Math.atan2(-y, x)); // Invert y-axis for game coordinates
        if (angle < 0) angle += 360; // Normalize to [0, 360]
        return (int) angle;
    }


    private void stopHuntMode() {
        // Optionally, stop any ongoing tasks or reset the bot's state
        sendCommand("noop");
    }

    private String findClosestEnemy() {
        try {
            sendCommand("scan");
            String line;
            double closestDistance = Double.MAX_VALUE;
            String closestDirection = null;

            while (!(line = in.readLine()).equals("scan done")) {
                if (line.startsWith("scan bot")) {
                    String[] parts = line.split(" ");
                    int x = Integer.parseInt(parts[3]); // X-coordinate
                    int y = Integer.parseInt(parts[4]); // Y-coordinate

                    double distance = Math.sqrt(x * x + y * y); // Distance formula
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestDirection = x + " " + y;
                    }
                }
            }
            return closestDirection;
        } catch (Exception e) {
            updateBattleLog("Error finding closest enemy: " + e.getMessage());
        }
        return null;
    }

    private void collectPowerUps() {
        try {
            sendCommand("scan");
            String line;

            while (!(line = in.readLine()).equals("scan done")) {
                if (line.startsWith("scan powerup")) {
                    String[] parts = line.split(" ");
                    int type = Integer.parseInt(parts[2]);
                    int x = Integer.parseInt(parts[3]); // X-coordinate
                    int y = Integer.parseInt(parts[4]); // Y-coordinate

                    if (type != 3) { // Avoid explosive power-ups
                        sendMoveCommand("move " + x + " " + y); // Move towards power-up
                    }
                }
            }
        } catch (Exception e) {
            updateBattleLog("Error collecting power-ups: " + e.getMessage());
        }
    }

}