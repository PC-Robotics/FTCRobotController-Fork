package org.firstinspires.ftc.teamcode.support;



import static org.firstinspires.ftc.teamcode.support.ServoMotorPosConstants.*;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

@Autonomous(name="four samples high bucket", group="Robot")
public class fourSampleHighBucket extends LinearOpMode
{
    JoulesRobot robot = new JoulesRobot(this, 0, false);

    public void runOpMode() {
        ElapsedTime timer = new ElapsedTime();
        robot.init();
        robot.imu.resetYaw();
        robot.wrist.setPosition(WRIST_DRIVING_POSITION);
        robot.bucket.setPosition(BUCKET_PICKUP_POSITION);
        updateTelemetryData();
        waitForStart();

        robot.linearSlideMove(LINEAR_SLIDE_HIGHEST_POSITION);

        robot.drive(24, MAX_DRIVING_POWER, AUTON_PID_HOLD_TIME);
    }

    private double radians(double degrees)
    {
        double radians = degrees * Math.PI / 180;
        return radians;
    }

    private void updateTelemetryData() {
        telemetry.addData("Subsystem Data ", "-----")
                .addData("Slide Position: ", robot.linearSlide.getCurrentPosition())
                .addData("Bucket Position: ", robot.bucket.getPosition())
                .addData("Trolley Positions: \nleft: ", robot.left.getPosition())
                .addData("Right: ", robot.right.getPosition())
                .addData("Claw Position: ", robot.claw.getPosition())
                .addData("Wrist Position: ", robot.wrist.getPosition())
                .addData("Happiness Score: ", "10/10");



        telemetry.update();
    }
}
