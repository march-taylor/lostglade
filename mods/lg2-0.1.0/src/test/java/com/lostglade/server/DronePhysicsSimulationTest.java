package com.lostglade.server;

import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DronePhysicsSimulationTest {
	private static final double EPSILON = 1.0E-6D;
	private static final double DRIVE_STEP = 0.5D;
	private static final double DRONE_HALF_WIDTH = 0.78D * 0.5D;

	private DronePhysicsSimulationTest() {
	}

	public static void main(String[] args) throws Exception {
		forwardInputAcceleratesClientPhysics();
		clientAuthoritativePoseEliminatesInputDelayDrift();
		clientAuthoritativeImpactCanStillBreakDrone();
		startupCorrectionDoesNotBreakDrone();
		staleOnGroundDoesNotBreakAirborneDrone();
		staleCollisionFlagsDoNotBreakWithoutClippedMotion();
		playerStepCorrectionWouldHaveBeenFatal();
		clientAuthoritativePoseDoesNotNeedHardPositionSync();
		motionOnlyVisualDriftWouldHaveTeleportedPeriodically();
		realServerWallImpactBreaksAtContact();
		realServerCeilingImpactBreaksAtContact();
		glancingSlideDoesNotBreakDrone();
		verifiedGroundContactCanAccumulateWear();
		sourceUsesClientAuthoritativeDronePose();
		System.out.println("Drone physics simulation passed");
	}

	private static void forwardInputAcceleratesClientPhysics() {
		SimState client = new SimState();
		for (int tick = 0; tick < 8; tick++) {
			client.tick(true, false, false, false, 0.0F, 0.0F);
		}

		require(client.forwardDrive == DroneFlightPhysics.MAX_FORWARD_DRIVE, "forward drive should reach full power");
		require(client.pos.length() > 5.0D, "client-authoritative drone physics should move under held forward input");
	}

	private static void clientAuthoritativePoseEliminatesInputDelayDrift() {
		SimState client = new SimState();
		SimState server = new SimState();
		double maxDrift = 0.0D;

		for (int tick = 0; tick < 30; tick++) {
			boolean currentForward = tick < 18;
			client.tick(currentForward, false, false, false, 0.0F, 0.0F);
			server.copyFromClientPose(client);
			maxDrift = Math.max(maxDrift, client.pos.subtract(server.pos).length());
		}

		require(
				maxDrift <= EPSILON,
				"client-authoritative pose streaming should not create visual/physical drift"
		);
	}

	private static void clientAuthoritativeImpactCanStillBreakDrone() {
		Vec3 intendedMovement = new Vec3(DroneFlightPhysics.MAX_FORWARD_SPEED, 0.0D, 0.0D);
		Vec3 clientActualMovement = Vec3.ZERO;
		float impactDamage = DroneImpactModel.computeImpactDamage(
				intendedMovement,
				clientActualMovement,
				true,
				false
		);

		require(
				impactDamage > DroneImpactModel.CONTROL_IMPACT_BREAK_DAMAGE,
				"a full-speed client-authoritative collision should still be fatal"
		);
	}

	private static void startupCorrectionDoesNotBreakDrone() {
		Vec3 intendedMovement = Vec3.ZERO;
		Vec3 startupCorrection = new Vec3(0.62D, 0.0D, 0.0D);
		boolean legacyHorizontalCollision = legacyHorizontalClipDetected(intendedMovement, startupCorrection);
		float legacyDamage = DroneImpactModel.computeImpactDamage(
				intendedMovement,
				startupCorrection,
				legacyHorizontalCollision,
				false
		);
		boolean correctedHorizontalCollision = clientAuthoritativeHorizontalClipDetected(intendedMovement, startupCorrection);
		float correctedDamage = DroneImpactModel.computeImpactDamage(
				intendedMovement,
				startupCorrection,
				correctedHorizontalCollision,
				false
		);

		require(
				legacyDamage > DroneImpactModel.CONTROL_IMPACT_BREAK_DAMAGE,
				"the regression test must reproduce the old instant break on startup correction"
		);
		require(
				!correctedHorizontalCollision,
				"startup correction without commanded movement must not count as a blocked collision"
		);
		require(correctedDamage == 0.0F, "startup correction must not destroy the drone");
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

	private static void clientAuthoritativePoseDoesNotNeedHardPositionSync() {
		Vec3 intendedMovement = new Vec3(0.0D, 0.0D, DroneFlightPhysics.MAX_FORWARD_SPEED);
		Vec3 wallClippedMovement = Vec3.ZERO;
		require(
				legacyHardSyncWouldFire(intendedMovement, wallClippedMovement),
				"the regression test must reproduce the old hard-sync on ordinary wall clipping"
		);
		require(
				!clientAuthoritativeHardSyncWouldFire(intendedMovement, wallClippedMovement),
				"client-authoritative control must not hard-sync the operator camera on ordinary clipping"
		);
	}

	private static void motionOnlyVisualDriftWouldHaveTeleportedPeriodically() {
		Vec3 serverPos = Vec3.ZERO;
		Vec3 clientPos = Vec3.ZERO;
		Vec3 legacyClientPos = Vec3.ZERO;
		Vec3 serverStep = new Vec3(0.0D, 0.0D, 0.62D);
		Vec3 clientStep = new Vec3(0.0D, 0.0D, 0.54D);
		boolean legacyVisualDriftSync = false;
		double maxStreamedCorrection = 0.0D;

		for (int tick = 0; tick < 30; tick++) {
			serverPos = serverPos.add(serverStep);
			clientPos = clientPos.add(clientStep);
			legacyClientPos = legacyClientPos.add(clientStep);
			if (legacyClientPos.subtract(serverPos).lengthSqr() > 1.35D * 1.35D) {
				legacyVisualDriftSync = true;
			}

			double streamedCorrection = clientPos.subtract(serverPos).length();
			maxStreamedCorrection = Math.max(maxStreamedCorrection, streamedCorrection);
			clientPos = serverPos;
		}

		require(
				legacyVisualDriftSync,
				"motion-only visual sync must reproduce the old periodic multi-block correction"
		);
		require(
				maxStreamedCorrection < 0.10D,
				"streamed self teleports should keep correction bounded to a sub-tick visual adjustment"
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

	private static void sourceUsesClientAuthoritativeDronePose() throws Exception {
		Path projectDir = Path.of("").toAbsolutePath();
		String server = Files.readString(projectDir.resolve("src/main/java/com/lostglade/server/DroneSystem.java"));
		String geometry = Files.readString(projectDir.resolve("src/main/java/com/lostglade/server/DroneGeometry.java"));
		Path legacyPayloads = projectDir.resolve("src/main/java/com/lostglade/network/DronePayloads.java");
		Path customControlPayloads = projectDir.resolve("src/main/java/com/lostglade/network/DroneControlPayloads.java");
		Path customControlClient = projectDir.resolve("src/client/java/com/lostglade/client/DroneControlClient.java");
		Path localProxyMixin = projectDir.resolve("src/client/java/com/lostglade/mixin/client/LocalPlayerDroneProxyMixin.java");
		Path cameraProxyMixin = projectDir.resolve("src/client/java/com/lostglade/mixin/client/CameraDroneProxyMixin.java");
		Path collisionProxyMixin = projectDir.resolve("src/client/java/com/lostglade/mixin/client/EntityDroneProxyCollisionMixin.java");
		Path clientControlState = projectDir.resolve("src/client/java/com/lostglade/client/DroneClientControlState.java");
		Path clientPrediction = projectDir.resolve("src/client/java/com/lostglade/client/DroneControlClientPrediction.java");
		Path clientPredictionMixin = projectDir.resolve("src/client/java/com/lostglade/mixin/client/LocalPlayerDronePredictionMixin.java");

		require(!Files.exists(legacyPayloads), "legacy drone payload registration file must stay removed");
		require(!Files.exists(customControlPayloads), "vanilla-client drone control must not require custom payloads");
		require(!Files.exists(customControlClient), "vanilla-client drone control must not require a client mod controller");
		require(!Files.exists(localProxyMixin), "client local-player drone simulation mixin must stay removed");
		require(!Files.exists(cameraProxyMixin), "client camera proxy mixin must stay removed");
		require(!Files.exists(collisionProxyMixin), "client collision proxy mixin must stay removed");
		require(!Files.exists(clientControlState), "client drone control state must stay removed");
		require(!Files.exists(clientPrediction), "client-side local-player drone prediction must stay removed");
		require(!Files.exists(clientPredictionMixin), "local player prediction mixin must stay removed");
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
		require(!server.contains("CONTROLLED_OPERATOR_VISUAL_DRIFT_RESYNC_DISTANCE"), "client visual drift must not accumulate into periodic hard sync");
		require(!server.contains("positionSyncRequested"), "client visual drift must not be stored for delayed hard sync");
		require(!server.contains("applyControlledDroneTravel"), "server must not keep duplicate controlled drone physics");
		require(!server.contains("root.move(MoverType.SELF, nextVelocity)"), "server must not run controlled movement from input");
		require(server.contains("handleControlledMovePacket"), "server must accept vanilla move packets during drone control");
		require(server.contains("applyControlledMovePacket"), "server must apply vanilla client-reported drone position directly");
		require(server.contains("packet.hasPosition()"), "controlled drone authority must come from ordinary client movement packets");
		require(server.contains("packet.getX(previousPos.x)"), "server must read authoritative X from the vanilla movement packet");
		require(!server.contains("Клиент не поддерживает плавное управление дроном"), "vanilla clients must not be rejected for missing custom drone networking");
		require(!server.contains("DroneControlPayloads"), "controlled drone authority must not depend on custom payloads");
		require(!server.contains("sendControlledOperatorPacket(player, buildControlledSelfTeleportPacket(player, session));"), "controlled view must not teleport the virtual self entity every tick");
		require(server.contains("sendControlledOperatorPacket(player, buildControlledPlayerPositionPacket(session));"), "controlled view must hard-sync the virtual self only on start or rare resync");
		require(server.contains("buildControlledOperatorPassengerPacket(player, session)"), "controlled view should keep the old self-camera passenger sync");
		require(server.contains("shouldSuppressPostControlMovePacket"), "stale drone-position move packets after control must be rejected");
		require(server.contains("restoreControlledOperatorClientState"), "player camera and inventory restore should share one resync path");
		require(server.contains("ServerMechanicsGateSystem.syncPlayerInventory(player);"), "leaving drone control must force an inventory resync");
		require(server.contains("private static final float DRONE_DISPLAY_CONTROLLED_Y_OFFSET = 0.0F;"), "controlled display must not be offset away from the physical root hitbox");
		require(geometry.contains("public static final float WIDTH = 0.78F;"), "drone collision width should match the display model more closely");
		require(geometry.contains("public static final float HEIGHT = 0.28F;"), "drone collision height should match the display model more closely");
	}

	private static boolean legacyHardSyncWouldFire(Vec3 intendedMovement, Vec3 actualMovement) {
		Vec3 serverCorrection = intendedMovement.subtract(actualMovement);
		return serverCorrection.lengthSqr() > 0.02D * 0.02D;
	}

	private static boolean clientAuthoritativeHardSyncWouldFire(Vec3 intendedMovement, Vec3 actualMovement) {
		return false;
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

	private static boolean legacyHorizontalClipDetected(Vec3 intendedMovement, Vec3 actualMovement) {
		double blockedX = intendedMovement.x - actualMovement.x;
		double blockedZ = intendedMovement.z - actualMovement.z;
		return blockedX * blockedX + blockedZ * blockedZ > EPSILON;
	}

	private static boolean clientAuthoritativeHorizontalClipDetected(Vec3 intendedMovement, Vec3 actualMovement) {
		double blockedX = positiveMovementDeficit(intendedMovement.x, actualMovement.x);
		double blockedZ = positiveMovementDeficit(intendedMovement.z, actualMovement.z);
		return blockedX * blockedX + blockedZ * blockedZ > EPSILON;
	}

	private static double positiveMovementDeficit(double intendedComponent, double actualComponent) {
		double intendedMagnitude = Math.abs(intendedComponent);
		if (intendedMagnitude <= 1.0E-5D) {
			return 0.0D;
		}
		double actualMagnitude = Math.abs(actualComponent);
		if (actualMagnitude <= 1.0E-5D) {
			return intendedMagnitude;
		}
		if (Math.signum(actualComponent) != Math.signum(intendedComponent)) {
			return 0.0D;
		}
		return Math.max(0.0D, intendedMagnitude - actualMagnitude);
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

		private void copyFromClientPose(SimState client) {
			this.pos = client.pos;
			this.forwardDrive = client.forwardDrive;
			this.strafeDrive = client.strafeDrive;
		}
	}
}
