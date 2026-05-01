package com.lostglade.server;

import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DronePhysicsSimulationTest {
	private static final double EPSILON = 1.0E-6D;
	private static final double DRIVE_STEP = 0.5D;
	private static final double DRONE_HALF_WIDTH = 0.95D * 0.5D;

	private DronePhysicsSimulationTest() {
	}

	public static void main(String[] args) throws Exception {
		forwardInputAcceleratesServerPhysics();
		oneTickInputDelayStaysBounded();
		clientOnlyCollisionSampleWouldHaveBeenFatal();
		staleOnGroundDoesNotBreakAirborneDrone();
		staleCollisionFlagsDoNotBreakWithoutClippedMotion();
		playerStepCorrectionWouldHaveBeenFatal();
		hardPositionSyncIgnoresOrdinaryWallClip();
		realServerWallImpactBreaksAtContact();
		realServerCeilingImpactBreaksAtContact();
		glancingSlideDoesNotBreakDrone();
		verifiedGroundContactCanAccumulateWear();
		sourceDoesNotRegisterClientCollisionSamples();
		System.out.println("Drone physics simulation passed");
	}

	private static void forwardInputAcceleratesServerPhysics() {
		SimState server = new SimState();
		for (int tick = 0; tick < 8; tick++) {
			server.tick(true, false, false, false, 0.0F, 0.0F);
		}

		require(server.forwardDrive == DroneFlightPhysics.MAX_FORWARD_DRIVE, "forward drive should reach full power");
		require(server.pos.length() > 5.0D, "server-authoritative drone physics should move under held forward input");
	}

	private static void oneTickInputDelayStaysBounded() {
		SimState client = new SimState();
		SimState server = new SimState();
		boolean previousForward = false;
		double maxDrift = 0.0D;

		for (int tick = 0; tick < 30; tick++) {
			boolean currentForward = tick < 18;
			client.tick(currentForward, false, false, false, 0.0F, 0.0F);
			server.tick(previousForward, false, false, false, 0.0F, 0.0F);
			maxDrift = Math.max(maxDrift, client.pos.subtract(server.pos).length());
			previousForward = currentForward;
		}

		require(
				maxDrift <= DroneFlightPhysics.MAX_COMBINED_SPEED + 0.15D,
				"ordinary one-tick input delay should not create multi-block visual/physical drift"
		);
	}

	private static void clientOnlyCollisionSampleWouldHaveBeenFatal() {
		Vec3 intendedMovement = new Vec3(DroneFlightPhysics.MAX_FORWARD_SPEED, 0.0D, 0.0D);
		Vec3 clientActualMovement = Vec3.ZERO;
		float oldClientSampleDamage = DroneImpactModel.computeImpactDamage(
				intendedMovement,
				clientActualMovement,
				true,
				false
		);

		require(
				oldClientSampleDamage > DroneImpactModel.CONTROL_IMPACT_BREAK_DAMAGE,
				"a full-speed client-only collision sample is fatal and must not be accepted as authority"
		);
	}

	private static void realServerWallImpactBreaksAtContact() {
		SimState server = new SimState();
		server.forwardDrive = DroneFlightPhysics.MAX_FORWARD_DRIVE;
		double wallZ = 4.0D;
		boolean broke = false;

		for (int tick = 0; tick < 20; tick++) {
			Vec3 intendedMovement = DroneFlightPhysics.step(0.0F, 0.0F, server.forwardDrive, 0.0D);
			require(intendedMovement.z > 0.0D, "simulation expects yaw 0 to move toward positive Z");
			Vec3 target = server.pos.add(intendedMovement);
			boolean horizontalCollision = target.z + DRONE_HALF_WIDTH > wallZ;
			Vec3 actualMovement = intendedMovement;
			if (horizontalCollision) {
				actualMovement = new Vec3(
						intendedMovement.x,
						intendedMovement.y,
						wallZ - DRONE_HALF_WIDTH - server.pos.z
				);
			}
			server.pos = server.pos.add(actualMovement);

			float damage = DroneImpactModel.computeImpactDamage(
					intendedMovement,
					actualMovement,
					horizontalCollision,
					false
			);
			if (damage > DroneImpactModel.CONTROL_IMPACT_BREAK_DAMAGE) {
				require(
						Math.abs(server.pos.z + DRONE_HALF_WIDTH - wallZ) <= EPSILON,
						"real impact should be detected exactly at the wall contact plane"
				);
				broke = true;
				break;
			}
		}

		require(broke, "server-side wall collision should break the drone at high normal speed");
	}

	private static void staleOnGroundDoesNotBreakAirborneDrone() {
		Vec3 intendedMovement = new Vec3(DroneFlightPhysics.MAX_FORWARD_SPEED, 0.0D, 0.0D);
		Vec3 actualMovement = intendedMovement;
		double legacyWear = 0.0D;
		double verifiedWear = 0.0D;

		for (int tick = 0; tick < 220; tick++) {
			legacyWear += legacySurfaceWearDelta(intendedMovement, actualMovement, true);
			DroneImpactModel.SurfaceWear surfaceWear = DroneImpactModel.computeSurfaceWear(
					intendedMovement,
					actualMovement,
					DroneImpactModel.hasVerifiedGroundWearContact(intendedMovement, actualMovement, false, false)
			);
			verifiedWear += surfaceWear.delta();
		}

		require(
				legacyWear >= DroneImpactModel.SURFACE_WEAR_BREAK_LEVEL,
				"the simulation must reproduce the old false break from onGround-only wear"
		);
		require(verifiedWear == 0.0D, "stale onGround without a real supporting collision must not wear the drone");
		require(
				DroneImpactModel.computeImpactDamage(intendedMovement, actualMovement, false, false) == 0.0F,
				"free flight with no clipped movement must not produce impact damage"
		);
	}

	private static void staleCollisionFlagsDoNotBreakWithoutClippedMotion() {
		Vec3 intendedMovement = new Vec3(0.42D, -0.18D, 0.42D);
		Vec3 actualMovement = intendedMovement;
		boolean verticalCollision = DroneImpactModel.hasMeaningfulVerticalCollision(
				intendedMovement,
				actualMovement,
				true,
				true
		);
		boolean groundContact = DroneImpactModel.hasVerifiedGroundWearContact(
				intendedMovement,
				actualMovement,
				true,
				true
		);

		require(!verticalCollision, "stale vertical flags without clipped Y movement must not count as impact");
		require(!groundContact, "stale vertical flags without clipped Y movement must not count as ground wear contact");
		require(
				DroneImpactModel.computeImpactDamage(intendedMovement, actualMovement, false, verticalCollision) == 0.0F,
				"stale collision flags without clipped movement must not damage the drone"
		);
		require(
				DroneImpactModel.computeSurfaceWear(intendedMovement, actualMovement, groundContact).delta() == 0.0D,
				"stale collision flags without clipped movement must not wear the drone"
		);
	}

	private static void playerStepCorrectionWouldHaveBeenFatal() {
		Vec3 intendedMovement = new Vec3(0.0D, 0.0D, DroneFlightPhysics.MAX_FORWARD_SPEED);
		Vec3 playerStepCorrectedMovement = new Vec3(0.62D, 0.60D, 0.0D);
		float damage = DroneImpactModel.computeImpactDamage(
				intendedMovement,
				playerStepCorrectedMovement,
				true,
				false
		);

		require(
				damage > DroneImpactModel.CONTROL_IMPACT_BREAK_DAMAGE,
				"player-only step correction can look like a fatal sideways drone impact"
		);
	}

	private static void hardPositionSyncIgnoresOrdinaryWallClip() {
		Vec3 intendedMovement = new Vec3(0.0D, 0.0D, DroneFlightPhysics.MAX_FORWARD_SPEED);
		Vec3 wallClippedMovement = Vec3.ZERO;
		require(
				legacyHardSyncWouldFire(intendedMovement, wallClippedMovement),
				"the regression test must reproduce the old hard-sync on ordinary wall clipping"
		);
		require(
				!currentHardSyncWouldFire(intendedMovement, wallClippedMovement),
				"ordinary collision clipping must not hard-sync the operator camera"
		);

		Vec3 anomalousServerJump = new Vec3(DroneFlightPhysics.MAX_COMBINED_SPEED + 1.0D, 0.0D, 0.0D);
		require(
				currentHardSyncWouldFire(Vec3.ZERO, anomalousServerJump),
				"large impossible server movement should still hard-sync the operator camera"
		);
	}

	private static void realServerCeilingImpactBreaksAtContact() {
		Vec3 intendedMovement = new Vec3(0.0D, DroneFlightPhysics.MAX_FORWARD_SPEED, 0.0D);
		Vec3 actualMovement = Vec3.ZERO;
		boolean verticalCollision = DroneImpactModel.hasMeaningfulVerticalCollision(
				intendedMovement,
				actualMovement,
				true,
				false
		);
		float damage = DroneImpactModel.computeImpactDamage(
				intendedMovement,
				actualMovement,
				false,
				verticalCollision
		);

		require(verticalCollision, "server-clipped upward movement should be a meaningful vertical collision");
		require(damage > DroneImpactModel.CONTROL_IMPACT_BREAK_DAMAGE, "hard ceiling impact should break the drone");
	}

	private static void glancingSlideDoesNotBreakDrone() {
		Vec3 intendedMovement = new Vec3(0.95D, 0.0D, 0.04D);
		Vec3 actualMovement = new Vec3(0.95D, 0.0D, 0.0D);
		float damage = DroneImpactModel.computeImpactDamage(intendedMovement, actualMovement, true, false);
		require(damage == 0.0F, "small glancing scrape should not count as a destructive impact");
	}

	private static void verifiedGroundContactCanAccumulateWear() {
		Vec3 intendedMovement = new Vec3(DroneFlightPhysics.MAX_FORWARD_SPEED, -0.05D, 0.0D);
		Vec3 actualMovement = new Vec3(DroneFlightPhysics.MAX_FORWARD_SPEED, 0.0D, 0.0D);
		boolean groundContact = DroneImpactModel.hasVerifiedGroundWearContact(
				intendedMovement,
				actualMovement,
				true,
				true
		);
		DroneImpactModel.SurfaceWear surfaceWear = DroneImpactModel.computeSurfaceWear(
				intendedMovement,
				actualMovement,
				groundContact
		);

		require(groundContact, "vertical clipping onto a supporting block should be verified ground contact");
		require(surfaceWear.delta() > 0.0D, "verified fast ground scrape should accumulate surface wear");
		require(
				DroneImpactModel.computeSurfaceWear(intendedMovement, actualMovement, false).delta() == 0.0D,
				"the same movement must not wear without verified ground contact"
		);
	}

	private static void sourceDoesNotRegisterClientCollisionSamples() throws Exception {
		Path projectDir = Path.of("").toAbsolutePath();
		String server = Files.readString(projectDir.resolve("src/main/java/com/lostglade/server/DroneSystem.java"));
		Path payloads = projectDir.resolve("src/main/java/com/lostglade/network/DronePayloads.java");
		Path localProxyMixin = projectDir.resolve("src/client/java/com/lostglade/mixin/client/LocalPlayerDroneProxyMixin.java");
		Path cameraProxyMixin = projectDir.resolve("src/client/java/com/lostglade/mixin/client/CameraDroneProxyMixin.java");
		Path collisionProxyMixin = projectDir.resolve("src/client/java/com/lostglade/mixin/client/EntityDroneProxyCollisionMixin.java");
		Path clientControlState = projectDir.resolve("src/client/java/com/lostglade/client/DroneClientControlState.java");

		require(!Files.exists(payloads), "drone payload registration file must stay removed");
		require(!Files.exists(localProxyMixin), "client local-player drone simulation mixin must stay removed");
		require(!Files.exists(cameraProxyMixin), "client camera proxy mixin must stay removed");
		require(!Files.exists(collisionProxyMixin), "client collision proxy mixin must stay removed");
		require(!Files.exists(clientControlState), "client drone control state must stay removed");
		require(!server.contains("handleControlledClientCollisionSample"), "server must not trust client collision samples");
		require(!server.contains("DroneCollisionSampleC2SPayload"), "collision sample payload must stay removed");
		require(!server.contains("verticalCollision || proxyPlayer.onGround()"), "controlled collision must not promote onGround into impact");
		require(!server.contains("updateControlledDroneSurfaceWear(session, root, intendedMovement, actualMovement, proxyPlayer.onGround()"), "surface wear must not be keyed by onGround alone");
		require(!server.contains("GameType.SPECTATOR.getId()"), "drone control must not spoof spectator mode");
		require(!server.contains("controlledProxyListener"), "drone control must not maintain a second packet listener");
		require(!server.contains("class DroneProxyPlayer"), "controlled drone physics must not be backed by a ServerPlayer subclass");
		require(!server.contains("controlledProxyPlayer"), "controlled drone physics must not keep a hidden ServerPlayer body");
		require(!server.contains("createControlledProxyPlayer"), "controlled drone startup must not create a hidden ServerPlayer body");
		require(!server.contains("serverCorrection.lengthSqr() >"), "ordinary collision clipping must not force hard operator position sync");
		require(server.contains("root.move(MoverType.SELF, nextVelocity)"), "controlled movement must move the drone root body");
		require(server.contains("prepareControlledDroneBody(root)"), "controlled movement must prepare the drone root hitbox");
		require(server.contains("recordControlledOperatorVisualPosition"), "client-reported position should only be used to detect visual drift");
	}

	private static boolean legacyHardSyncWouldFire(Vec3 intendedMovement, Vec3 actualMovement) {
		Vec3 serverCorrection = intendedMovement.subtract(actualMovement);
		return serverCorrection.lengthSqr() > 0.02D * 0.02D;
	}

	private static boolean currentHardSyncWouldFire(Vec3 intendedMovement, Vec3 actualMovement) {
		double maxExpectedMovement = Math.max(intendedMovement.length(), DroneFlightPhysics.MAX_COMBINED_SPEED) + 0.02D;
		double anomalousMovement = Math.max(maxExpectedMovement, DroneFlightPhysics.MAX_COMBINED_SPEED + 0.35D);
		if (actualMovement.lengthSqr() <= anomalousMovement * anomalousMovement) {
			return false;
		}
		return actualMovement.subtract(intendedMovement).lengthSqr() > 0.55D * 0.55D;
	}

	private static double legacySurfaceWearDelta(Vec3 intendedMovement, Vec3 actualMovement, boolean onGround) {
		if (!onGround) {
			return 0.0D;
		}
		double tangentialSpeed = Math.sqrt(actualMovement.x * actualMovement.x + actualMovement.z * actualMovement.z);
		if (tangentialSpeed < DroneImpactModel.SURFACE_WEAR_MIN_TANGENTIAL_SPEED) {
			return 0.0D;
		}
		DroneImpactModel.Forces forces = DroneImpactModel.computeForces(
				intendedMovement,
				actualMovement,
				false,
				true
		);
		double downwardPressure = Math.max(forces.normalSpeed(), DroneImpactModel.SURFACE_WEAR_BASE_GROUND_PRESSURE);
		double speedFactor = net.minecraft.util.Mth.clamp(
				(tangentialSpeed - DroneImpactModel.SURFACE_WEAR_MIN_TANGENTIAL_SPEED)
						/ Math.max(0.001D, DroneFlightPhysics.MAX_COMBINED_SPEED - DroneImpactModel.SURFACE_WEAR_MIN_TANGENTIAL_SPEED),
				0.0D,
				1.0D
		);
		double pressureFactor = net.minecraft.util.Mth.clamp(
				downwardPressure / DroneImpactModel.SURFACE_WEAR_REFERENCE_PRESSURE,
				0.0D,
				1.0D
		);
		return Math.min(
				DroneImpactModel.SURFACE_WEAR_MAX_DELTA_PER_TICK,
				(0.004D + 0.055D * pressureFactor) * speedFactor * speedFactor
		);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class SimState {
		private Vec3 pos = Vec3.ZERO;
		private double forwardDrive;
		private double strafeDrive;

		private void tick(
				boolean forward,
				boolean backward,
				boolean right,
				boolean left,
				float pitch,
				float yaw
		) {
			this.forwardDrive = DroneFlightPhysics.adjustDrive(
					this.forwardDrive,
					forward,
					backward,
					DRIVE_STEP,
					DroneFlightPhysics.MAX_FORWARD_DRIVE
			);
			this.strafeDrive = DroneFlightPhysics.adjustDrive(
					this.strafeDrive,
					right,
					left,
					DRIVE_STEP,
					DroneFlightPhysics.MAX_STRAFE_DRIVE
			);
			this.pos = this.pos.add(DroneFlightPhysics.step(pitch, yaw, this.forwardDrive, this.strafeDrive));
		}
	}
}
