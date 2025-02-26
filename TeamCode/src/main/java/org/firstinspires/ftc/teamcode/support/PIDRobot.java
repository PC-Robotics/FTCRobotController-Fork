package org.firstinspires.ftc.teamcode.support;


import static org.firstinspires.ftc.teamcode.support.ConfigConstants.DRIVE_KD;
import static org.firstinspires.ftc.teamcode.support.ConfigConstants.DRIVE_KI;
import static org.firstinspires.ftc.teamcode.support.ConfigConstants.DRIVE_KP;
import static org.firstinspires.ftc.teamcode.support.ConfigConstants.STRAFE_KD;
import static org.firstinspires.ftc.teamcode.support.ConfigConstants.STRAFE_KI;
import static org.firstinspires.ftc.teamcode.support.ConfigConstants.STRAFE_KP;
import static org.firstinspires.ftc.teamcode.support.ConfigConstants.YAW_KD;
import static org.firstinspires.ftc.teamcode.support.ConfigConstants.YAW_KI;
import static org.firstinspires.ftc.teamcode.support.ConfigConstants.YAW_KP;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.DRIVE_ACCEL;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.DRIVE_MAX_AUTO;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.DRIVE_TOLERANCE;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.INVERT_DRIVE_ODOMETRY;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.INVERT_STRAFE_ODOMETRY;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.ODOM_INCHES_PER_COUNT;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.STRAFE_ACCEL;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.STRAFE_MAX_AUTO;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.STRAFE_TOLERANCE;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.YAW_ACCEL;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.YAW_MAX_AUTO;
import static org.firstinspires.ftc.teamcode.support.OdometryConstants.YAW_TOLERANCE;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.telemetry.MultipleTelemetry;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AngularVelocity;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

import java.util.List;

public class PIDRobot
{
    protected LinearOpMode myOpMode = null;

    private DcMotor frontRight   = null;
    private DcMotor frontLeft  = null;
    private DcMotor backRight = null;
    private DcMotor backLeft = null;
    private DcMotor driveEncoder = null;
    private DcMotor strafeEncoder = null;

    public IMU imu = null;

    private ElapsedTime holdTimer = new ElapsedTime();
    private ElapsedTime moveTimer = new ElapsedTime();

    // Public Members
    public double driveDistance     = 0; // scaled axial distance (+ = forward)
    public double strafeDistance    = 0; // scaled lateral distance (+ = left)
    public double heading           = 0; // Latest Robot heading from IMU

    private int rawDriveOdometer    = 0; // Unmodified axial odometer count
    private int driveOdometerOffset = 0; // Used to offset axial odometer
    private int rawStrafeOdometer   = 0; // Unmodified lateral odometer count
    private int strafeOdometerOffset= 0; // Used to offset lateral odometer
    private double rawHeading       = 0; // Unmodified heading (degrees)
    private double headingOffset    = 0; // Used to offset heading

    public PIDController driveController = new PIDController(DRIVE_KP,DRIVE_KI,DRIVE_KD,DRIVE_ACCEL,DRIVE_MAX_AUTO,DRIVE_TOLERANCE,false);
    public PIDController strafeController = new PIDController(STRAFE_KP,STRAFE_KI,STRAFE_KD,STRAFE_ACCEL,STRAFE_MAX_AUTO,STRAFE_TOLERANCE,false);
    public PIDController yawController = new PIDController(YAW_KP,YAW_KI,YAW_KD,YAW_ACCEL,YAW_MAX_AUTO,YAW_TOLERANCE,true);

    private double turnRate         = 0; // Latest Robot Turn Rate from IMU
    private boolean showTelemetry   = false;

    private int fieldCentric = 0;

    public PIDRobot (LinearOpMode opMode, boolean isFieldCentric)
    {
        myOpMode = opMode;

        // Allows for coding a robot to be in robot-centric or field/driver-centric
        if(isFieldCentric)
        {
            fieldCentric = 1;
        }
    }

    /**
     * Robot Initialization:
     *   Use the hardware map to connect all devices.
     *   Perform any setup of connected devices
     * @param showTelemetry  Set to TRUE if  you want to display PID telemetry outputs
     */
    public void init(boolean showTelemetry)
    {


        // Initialize the drive train motors
        // Currently running them with encoders to use the PID controls and odometry
        frontRight = setupMotor("frontRight", DcMotor.Direction.FORWARD, true);
        frontLeft = setupMotor("frontLeft",DcMotor.Direction.REVERSE,true);
        backRight = setupMotor("backRight",DcMotor.Direction.FORWARD, true);
        backLeft = setupMotor("backLeft",DcMotor.Direction.REVERSE, true);

        // Initialize the IMU
        imu = myOpMode.hardwareMap.get(IMU.class,"imu");

        // Initialize the odometry wheels
        driveEncoder = myOpMode.hardwareMap.get(DcMotor.class,"par");
        strafeEncoder = myOpMode.hardwareMap.get(DcMotor.class,"perp");

        /*
         * Copied from another source (Simpflied Odometry by gearsincorg)
         * Claims that this code makes the encoder readouts faster
         * Also referenced in External Samples ConceptMotorBulkRead
         */
        List<LynxModule> allHubs = myOpMode.hardwareMap.getAll(LynxModule.class);
        for(LynxModule module : allHubs)
        {
            module.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
        }

        // Initialize the IMU. Remember to check how this is orientated on any new robots
        // TODO: When copying this code into a new robot, make sure the orientation is set correctly
        RevHubOrientationOnRobot orientationOnRobot =
                new RevHubOrientationOnRobot(RevHubOrientationOnRobot.LogoFacingDirection.UP,
                        RevHubOrientationOnRobot.UsbFacingDirection.LEFT);
        imu.initialize(new IMU.Parameters(orientationOnRobot));

        // Zero out all odometry
        resetOdometry();


    }

    /**
     * Motor initialization based on hardwareMap name, run direction,
     * and whether or not to use encoders
     * @param motorName The motor to be accessed from the hardware map
     * @param direction Either FORWARD or REVERSE
     * @param encoder   true - run WITH encoders, false - run WITHOUT encoders
     * @return a newly instantiated motor
     */
    public DcMotor setupMotor(String motorName, DcMotor.Direction direction, boolean encoder)
    {
        DcMotor aMotor = myOpMode.hardwareMap.get(DcMotor.class,motorName);
        aMotor.setDirection(direction);
        if(encoder)
        {
            aMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            aMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        }
        else aMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        aMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        return aMotor;
    }

    /**
     * Read all input devices to determine the robot's motion
     * always return true so this can be used in "while" loop conditions
     * @return true
     */
    public boolean readSensors()
    {
        rawDriveOdometer = driveEncoder.getCurrentPosition() * (INVERT_DRIVE_ODOMETRY ? -1 : 1);
        rawStrafeOdometer = strafeEncoder.getCurrentPosition() * (INVERT_STRAFE_ODOMETRY ? -1 : 1);
        driveDistance = (rawDriveOdometer - driveOdometerOffset) * ODOM_INCHES_PER_COUNT;
        strafeDistance = (rawStrafeOdometer - strafeOdometerOffset) * ODOM_INCHES_PER_COUNT;

        YawPitchRollAngles orientation = imu.getRobotYawPitchRollAngles();
        AngularVelocity angularVelocity = imu.getRobotAngularVelocity(AngleUnit.DEGREES);

        rawHeading  = orientation.getYaw(AngleUnit.RADIANS);
        heading     = rawHeading - headingOffset;
        turnRate    = angularVelocity.zRotationRate;

        myOpMode.telemetry.addData("Drive Distance :: ",driveDistance);
        myOpMode.telemetry.addData("Strafe Distance :: ",strafeDistance);
        myOpMode.telemetry.addData("Heading :: ",heading);
        myOpMode.telemetry.addData("Turn Rate :: ",turnRate);

        return true;  // do this so this function can be included in the condition for a while loop to keep values fresh.
    }

    //  ########################  Mid level control functions.  #############################3#
    public void drive(double distanceInches, double power, double holdTime) {
        drive(distanceInches, power, holdTime, Double.POSITIVE_INFINITY);
    }
    /**
     * Drive in the axial (forward/reverse) direction, maintain the current heading and don't drift sideways
     * @param distanceInches  Distance to travel.  +ve = forward, -ve = reverse.
     * @param power Maximum power to apply.  This number should always be positive.
     * @param holdTime Minimum time (sec) required to hold the final position.  0 = no hold.
     */
    public void drive(double distanceInches, double power, double holdTime, double timeout) {
        resetOdometry();

        driveController.reset(distanceInches, power);   // achieve desired drive distance
        strafeController.reset(0);              // Maintain zero strafe drift
        yawController.reset();                          // Maintain last turn heading
        holdTimer.reset();
        moveTimer.reset();

        while (myOpMode.opModeIsActive() && readSensors() && moveTimer.seconds() < timeout){

            // implement desired axis powers
            moveFixed(strafeController.getOutput(strafeDistance), -driveController.getOutput(driveDistance),yawController.getOutput(heading) ,power);

            // Time to exit?
            if (driveController.inPosition() && yawController.inPosition() && strafeController.inPosition()) {
                if (holdTimer.seconds() > holdTime) {
                    break;   // Exit loop if we are in position, and have been there long enough.
                }
            } else {
                holdTimer.reset();
            }
            myOpMode.sleep(10);

            myOpMode.telemetry.clearAll();
            myOpMode.telemetry.addData("Drive Error", driveController.lastError);
            myOpMode.telemetry.addData("Drive Output", driveController.lastOutput);
            myOpMode.telemetry.addData("Strafe Error", strafeController.lastError);
            myOpMode.telemetry.addData("Strafe Output", strafeController.lastOutput);
            myOpMode.telemetry.addData("Yaw Error", yawController.lastError);
            myOpMode.telemetry.addData("Yaw Output", yawController.lastOutput);
            myOpMode.telemetry.update();
        }
        stopRobot();
    }

    public void strafe(double distanceInches, double power, double holdTime) {
        strafe(distanceInches, power, holdTime, Double.POSITIVE_INFINITY);
    }

    /**
     * Strafe in the lateral (left/right) direction, maintain the current heading and don't drift fwd/bwd
     * @param distanceInches  Distance to travel.  -ve = left, +ve = right.
     * @param power Maximum power to apply.  This number should always be positive.
     * @param holdTime Minimum time (sec) required to hold the final position.  0 = no hold.
     */
    public void strafe(double distanceInches, double power, double holdTime, double timeout) {
        resetOdometry();
        driveController.reset(0.0);             //  Maintain zero drive drift
        strafeController.reset(distanceInches, power);  // Achieve desired Strafe distance
        yawController.reset();                          // Maintain last turn angle
        holdTimer.reset();
        moveTimer.reset();

        while (myOpMode.opModeIsActive() && readSensors() && moveTimer.seconds() < timeout){

            // implement desired axis powers
            moveFixed(strafeController.getOutput(strafeDistance), -driveController.getOutput(driveDistance),yawController.getOutput(heading),power);

            // Time to exit?
            if (strafeController.inPosition() && yawController.inPosition() && driveController.inPosition()) {
                if (holdTimer.time() > holdTime) {
                    break;   // Exit loop if we are in position, and have been there long enough.
                }
            } else {
                holdTimer.reset();
            }
            myOpMode.sleep(10);

            myOpMode.telemetry.clearAll();
            myOpMode.telemetry.addData("Drive Error", driveController.lastError);
            myOpMode.telemetry.addData("Drive Output", driveController.lastOutput);
            myOpMode.telemetry.addData("Strafe Error", strafeController.lastError);
            myOpMode.telemetry.addData("Strafe Output", strafeController.lastOutput);
            myOpMode.telemetry.addData("Yaw Error", yawController.lastError);
            myOpMode.telemetry.addData("Yaw Output", yawController.lastOutput);
            myOpMode.telemetry.update();
        }
        stopRobot();
    }

    public void turnTo(double headingRad, double power, double holdTime) {
        turnTo(headingRad, power, holdTime, Double.POSITIVE_INFINITY);
    }
    /**
     * Rotate to an absolute heading/direction.
     * Note: this is relative to the robot start location unless resetHeading() is used
     * @param headingRad  Heading to obtain.  +ve = CCW, -ve = CW.
     * @param power Maximum power to apply.  This number should always be positive.
     * @param holdTime Minimum time (sec) required to hold the final position.  0 = no hold.
     */
    public void turnTo(double headingRad, double power, double holdTime, double timeout) {
        yawController.reset(headingRad, power);     // KD .21    KI  .08  KP 1.55
        holdTimer.reset();
        moveTimer.reset();

        while (myOpMode.opModeIsActive() && readSensors() && moveTimer.seconds() < timeout) {

            // implement desired axis powers
            moveFixed(0, 0, yawController.getOutput(heading),power);

            // Time to exit?
            if (yawController.inPosition()) {
                if (holdTimer.time() > holdTime) {
                    break;   // Exit loop if we are in position, and have been there long enough.
                }
            } else {
                holdTimer.reset();
            }
            myOpMode.sleep(10);

            myOpMode.telemetry.clearAll();
            myOpMode.telemetry.addData("Drive Error", driveController.lastError);
            myOpMode.telemetry.addData("Drive Output", driveController.lastOutput);
            myOpMode.telemetry.addData("Strafe Error", strafeController.lastError);
            myOpMode.telemetry.addData("Strafe Output", strafeController.lastOutput);
            myOpMode.telemetry.addData("Yaw Error", yawController.lastError);
            myOpMode.telemetry.addData("Yaw Output", yawController.lastOutput);
            myOpMode.telemetry.update();
        }
        stopRobot();
    }

    public void moveToPointRelative(double distanceDriveInches, double distanceStrafeInches, double power, double holdTime) {
        moveToPointRelative(distanceDriveInches, distanceStrafeInches, power, holdTime, Double.POSITIVE_INFINITY);
    }
    /**
     * Drive in the axial (forward/reverse) direction, maintain the current heading and don't drift sideways
     * @param distanceInches  Distance to travel.  +ve = forward, -ve = reverse.
     * @param power Maximum power to apply.  This number should always be positive.
     * @param holdTime Minimum time (sec) required to hold the final position.  0 = no hold.
     */
    public void moveToPointRelative(double distanceDriveInches, double distanceStrafeInches, double power, double holdTime, double timeout) {
        resetOdometry();

        driveController.reset(distanceDriveInches, power);   // achieve desired drive distance
        strafeController.reset(distanceStrafeInches, power);              // Maintain zero strafe drift
        yawController.reset();                          // Maintain last turn heading
        holdTimer.reset();
        moveTimer.reset();

        while (myOpMode.opModeIsActive() && readSensors() && moveTimer.seconds() < timeout){

            // implement desired axis powers
            moveFixed(strafeController.getOutput(strafeDistance), -driveController.getOutput(driveDistance),yawController.getOutput(heading) ,power);

            // Time to exit?
            if (driveController.inPosition() && yawController.inPosition() && strafeController.inPosition()) {
                if (holdTimer.seconds() > holdTime) {
                    break;   // Exit loop if we are in position, and have been there long enough.
                }
            } else {
                holdTimer.reset();
            }
            myOpMode.sleep(10);

            myOpMode.telemetry.clearAll();
            myOpMode.telemetry.addData("Drive Error", driveController.lastError);
            myOpMode.telemetry.addData("Drive Output", driveController.lastOutput);
            myOpMode.telemetry.addData("Strafe Error", strafeController.lastError);
            myOpMode.telemetry.addData("Strafe Output", strafeController.lastOutput);
            myOpMode.telemetry.addData("Yaw Error", yawController.lastError);
            myOpMode.telemetry.addData("Yaw Output", yawController.lastOutput);
            myOpMode.telemetry.update();
        }
        stopRobot();
    }

    public void moveToPoseRelative(double distanceDriveInches, double distanceStrafeInches, double targetHeadingRadians, double power, double holdTime) {
        moveToPoseRelative(distanceDriveInches, distanceStrafeInches, targetHeadingRadians, power, holdTime, Double.POSITIVE_INFINITY);
    }
    /**
     * Drive in the axial (forward/reverse) direction, maintain the current heading and don't drift sideways
     * @param distanceInches  Distance to travel.  +ve = forward, -ve = reverse.
     * @param power Maximum power to apply.  This number should always be positive.
     * @param holdTime Minimum time (sec) required to hold the final position.  0 = no hold.
     */
    public void moveToPoseRelative(double distanceDriveInches, double distanceStrafeInches, double targetHeadingRadians, double power, double holdTime, double timeout) {
        resetOdometry();

        driveController.reset(distanceDriveInches, power);   // achieve desired drive distance
        strafeController.reset(distanceStrafeInches, power);              // Maintain zero strafe drift
        yawController.reset(targetHeadingRadians, power);                          // Maintain last turn heading
        holdTimer.reset();
        moveTimer.reset();

        while (myOpMode.opModeIsActive() && readSensors() && moveTimer.seconds() < timeout){

            // implement desired axis powers
            moveFixed(strafeController.getOutput(strafeDistance), -driveController.getOutput(driveDistance),yawController.getOutput(heading) ,power);

            // Time to exit?
            if (driveController.inPosition() && yawController.inPosition() && strafeController.inPosition()) {
                if (holdTimer.seconds() > holdTime) {
                    break;   // Exit loop if we are in position, and have been there long enough.
                }
            } else {
                holdTimer.reset();
            }
            myOpMode.sleep(10);

            myOpMode.telemetry.clearAll();
            myOpMode.telemetry.addData("Drive Error", driveController.lastError);
            myOpMode.telemetry.addData("Drive Output", driveController.lastOutput);
            myOpMode.telemetry.addData("Strafe Error", strafeController.lastError);
            myOpMode.telemetry.addData("Strafe Output", strafeController.lastOutput);
            myOpMode.telemetry.addData("Yaw Error", yawController.lastError);
            myOpMode.telemetry.addData("Yaw Output", yawController.lastOutput);
            myOpMode.telemetry.update();
        }
        stopRobot();
    }


    //  ########################  Low level control functions.  ###############################

    /**
     * Drive the wheel motors to obtain the requested axes motions
     * @param x     gamepad1 leftStick  x
     * @param y     gamepad1 leftStick  y
     * @param turn  gamepad1 rightStick x
     */
    public void moveRobot(double x, double y, double turn,double maxPower)
    {
        double theta;
        double power;
        double sin;
        double cos;
        double max;

        double fL;
        double fR;
        double bL;
        double bR;

        // If fieldCentric controls, adjust the angle based on the robot's orientation
        // Otherwise, fieldCentric = 0 and the angle is based only on the input
        // theta is the angle the left stick is pressed / you want the robot to move
        theta = Math.atan2(y, x) + fieldCentric * (rawHeading+Math.PI);
        power = Math.hypot(x, y);

        // Values are offset by PI / 4 because of mecanum wheels
        sin = Math.sin(theta - Math.PI / 4);
        cos = Math.cos(theta - Math.PI / 4);
        max = Math.max(Math.abs(sin), Math.abs(cos));

        // Divide by max to scale the sin and cos up to a value of 1
        fL = power * sin / max + turn;
        fR = power * cos / max - turn;

        bL = power * cos / max + turn;
        bR = power * sin / max - turn;

        // If the power + |turn| is above max power, need to scale everything down
        double largest = power + Math.abs(turn);
        if (largest > maxPower)
        {
            // This set of commands sets the maximum value of any one motor power to 1
            fL /= largest;
            fR /= largest;
            bL /= largest;
            bR /= largest;

            // This set of commands then scales it based on the max power input
            // Typically in autonomous modes this value is less than 1 in case something goes wrong
            fL *= maxPower;
            fR *= maxPower;
            bL *= maxPower;
            bR *= maxPower;
        }

        frontLeft.setPower(fL);
        frontRight.setPower(fR);
        backLeft.setPower(bL);
        backRight.setPower(bR);
    }

    public void moveFixed(double x, double y, double turn, double maxPower)
    {
        double fL;
        double fR;
        double bL;
        double bR;

        fL = y + x + turn;
        fR = y - x - turn;
        bL = y - x + turn;
        bR = y + x - turn;

        double largest = Math.max(Math.max(Math.abs(fL), Math.abs(fR)), Math.max(Math.abs(bL), Math.abs(bR)));
        if (largest > maxPower)
        {
            fL /= largest;
            fR /= largest;
            bL /= largest;
            bR /= largest;

            fL *= maxPower;
            fR *= maxPower;
            bL *= maxPower;
            bR *= maxPower;
        }

        frontLeft.setPower(fL);
        frontRight.setPower(fR);
        backLeft.setPower(bL);
        backRight.setPower(bR);
    }

    /**
     * Stop all motors.
     */
    public void stopRobot() {
        moveRobot(0,0,0,1);
    }

    public void setPowers(double FLpower, double FRpower, double BLpower, double BRpower) {
        frontLeft.setPower(FLpower);
        frontRight.setPower(FRpower);
        backLeft.setPower(BLpower);
        backRight.setPower(BRpower);
    }
    /**
     * Set odometry counts and distances to zero.
     */
    public void resetOdometry() {
        readSensors();
        driveOdometerOffset = rawDriveOdometer;
        driveDistance = 0.0;
        driveController.reset(0);

        strafeOdometerOffset = rawStrafeOdometer;
        strafeDistance = 0.0;
        strafeController.reset(0);
    }

    /**
     * Reset the robot heading to zero degrees, and also lock that heading into heading controller.
     * Useful when giving new commands based on robot's current positioning instead of where it started
     */
    public void resetHeading() {
        readSensors();
        headingOffset = rawHeading;
        yawController.reset(0);
        heading = 0;
    }

    public double getHeading() {return heading;}
    public double getTurnRate() {return turnRate;}

    /**
     * Set the drive telemetry on or off
     */
    public void showTelemetry(boolean show){
        showTelemetry = show;
    }
}