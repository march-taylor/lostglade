import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Patches cool_elytra so that:
 * 1) The roll computation doesn't saturate when flying backwards (uses acos(abs(cosine))).
 * 2) The mod only applies while "drone-flying" (heuristic: client player is fall-flying AND has passengers).
 *
 * This is a bytecode patcher intended for a locally installed mod JAR.
 */
public final class CoolElytraDroneOnlyPatcher {
	private static final String GAME_RENDERER_MIXIN = "edu/jorbonism/cool_elytra/mixin/GameRendererMixin.class";
	private static final String CLIENT_PLAYER_MIXIN = "edu/jorbonism/cool_elytra/mixin/ClientPlayerEntityMixin.class";

	private static final String MATH = "java/lang/Math";
	private static final String MC_ENTITY = "net/minecraft/class_1297";
	private static final String MC_MINECRAFT = "net/minecraft/class_310";
	private static final String MC_CLIENT_PLAYER = "net/minecraft/class_746";

	private CoolElytraDroneOnlyPatcher() {}

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("Usage: java CoolElytraDroneOnlyPatcher <input.jar> <output.jar>");
			System.exit(2);
		}

		Path input = Paths.get(args[0]);
		Path output = Paths.get(args[1]);
		PatchReport report = patchJar(input, output);
		System.out.println(report);
	}

	private static PatchReport patchJar(Path inputJar, Path outputJar) throws IOException {
		Objects.requireNonNull(inputJar, "inputJar");
		Objects.requireNonNull(outputJar, "outputJar");

		PatchReport report = new PatchReport(inputJar.toString(), outputJar.toString());
		Files.createDirectories(outputJar.toAbsolutePath().getParent());

		try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(inputJar));
			 ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outputJar))) {
			ZipEntry entry;
			while ((entry = zin.getNextEntry()) != null) {
				String name = entry.getName();
				byte[] original = readAllBytes(zin);
				byte[] patched = original;

				if (GAME_RENDERER_MIXIN.equals(name)) {
					PatchResult result = patchGameRendererMixin(original);
					patched = result.bytes();
					report.gameRendererAcosAbsInserted += result.acosAbsInserted();
					report.gameRendererPassengerGatesInserted += result.passengerGatesInserted();
					report.foundGameRendererMixin = true;
				} else if (CLIENT_PLAYER_MIXIN.equals(name)) {
					PatchResult result = patchClientPlayerMixin(original);
					patched = result.bytes();
					report.clientPlayerPassengerGatesInserted += result.passengerGatesInserted();
					report.foundClientPlayerMixin = true;
				}

				ZipEntry outEntry = new ZipEntry(name);
				if (entry.getTime() != -1) {
					outEntry.setTime(entry.getTime());
				}
				if (entry.getComment() != null) {
					outEntry.setComment(entry.getComment());
				}
				if (entry.getExtra() != null) {
					outEntry.setExtra(entry.getExtra());
				}
				// Always deflate; avoids having to recompute CRC/size for STORED entries.
				outEntry.setMethod(ZipEntry.DEFLATED);
				zout.putNextEntry(outEntry);
				zout.write(patched);
				zout.closeEntry();
			}
		}

		return report;
	}

	private static PatchResult patchGameRendererMixin(byte[] original) {
		ClassNode cn = readClass(original);
		int absInserted = 0;
		int passengerGates = 0;

		for (MethodNode mn : cn.methods) {
			if (!"renderWorld".equals(mn.name)) {
				continue;
			}
			absInserted += insertAbsBeforeAcos(mn);
			passengerGates += insertPassengerGateAfterFallFlyingCheckInGameRenderer(mn, cn.name);
		}

		byte[] bytes = writeClass(cn);
		return new PatchResult(bytes, absInserted, passengerGates);
	}

	private static PatchResult patchClientPlayerMixin(byte[] original) {
		ClassNode cn = readClass(original);
		int passengerGates = 0;

		for (MethodNode mn : cn.methods) {
			if (!"method_5872".equals(mn.name) || !"(DD)V".equals(mn.desc)) {
				continue;
			}
			passengerGates += insertPassengerGateAfterFallFlyingCheckInClientPlayerTurn(mn);
		}

		byte[] bytes = writeClass(cn);
		return new PatchResult(bytes, 0, passengerGates);
	}

	private static int insertAbsBeforeAcos(MethodNode mn) {
		int inserted = 0;
		for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode mi)) {
				continue;
			}
			if (mi.getOpcode() != Opcodes.INVOKESTATIC
					|| !MATH.equals(mi.owner)
					|| !"acos".equals(mi.name)
					|| !"(D)D".equals(mi.desc)) {
				continue;
			}

			AbstractInsnNode prev = previousMeaningful(insn.getPrevious());
			if (prev instanceof MethodInsnNode pmi
					&& pmi.getOpcode() == Opcodes.INVOKESTATIC
					&& MATH.equals(pmi.owner)
					&& "abs".equals(pmi.name)
					&& "(D)D".equals(pmi.desc)) {
				continue; // already patched
			}

			mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "abs", "(D)D", false));
			inserted++;
		}
		return inserted;
	}

	private static int insertPassengerGateAfterFallFlyingCheckInGameRenderer(MethodNode mn, String ownerInternalName) {
		int inserted = 0;
		for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode mi)) {
				continue;
			}
			if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL
					|| !MC_CLIENT_PLAYER.equals(mi.owner)
					|| !"method_6128".equals(mi.name)
					|| !"()Z".equals(mi.desc)) {
				continue;
			}

			AbstractInsnNode next = nextMeaningful(mi.getNext());
			if (!(next instanceof JumpInsnNode jump) || jump.getOpcode() != Opcodes.IFEQ) {
				continue;
			}

			if (looksLikeAlreadyPatchedPassengerGateInGameRenderer(jump, ownerInternalName)) {
				continue;
			}

			InsnList gate = new InsnList();
			gate.add(new VarInsnNode(Opcodes.ALOAD, 0));
			gate.add(new FieldInsnNode(Opcodes.GETFIELD, ownerInternalName, "field_4015", "L" + MC_MINECRAFT + ";"));
			gate.add(new FieldInsnNode(Opcodes.GETFIELD, MC_MINECRAFT, "field_1724", "L" + MC_CLIENT_PLAYER + ";"));
			gate.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MC_ENTITY, "method_5782", "()Z", false));
			gate.add(new JumpInsnNode(Opcodes.IFEQ, jump.label));

			mn.instructions.insert(jump, gate);
			inserted++;
		}
		return inserted;
	}

	private static boolean looksLikeAlreadyPatchedPassengerGateInGameRenderer(JumpInsnNode fallFlyingIfeq, String ownerInternalName) {
		AbstractInsnNode n = nextMeaningful(fallFlyingIfeq.getNext());
		if (!(n instanceof VarInsnNode a0) || a0.getOpcode() != Opcodes.ALOAD || a0.var != 0) {
			return false;
		}
		n = nextMeaningful(n.getNext());
		if (!(n instanceof FieldInsnNode f1)
				|| f1.getOpcode() != Opcodes.GETFIELD
				|| !ownerInternalName.equals(f1.owner)
				|| !"field_4015".equals(f1.name)) {
			return false;
		}
		n = nextMeaningful(n.getNext());
		if (!(n instanceof FieldInsnNode f2)
				|| f2.getOpcode() != Opcodes.GETFIELD
				|| !MC_MINECRAFT.equals(f2.owner)
				|| !"field_1724".equals(f2.name)) {
			return false;
		}
		n = nextMeaningful(n.getNext());
		if (!(n instanceof MethodInsnNode mi)
				|| mi.getOpcode() != Opcodes.INVOKEVIRTUAL
				|| !MC_ENTITY.equals(mi.owner)
				|| !"method_5782".equals(mi.name)
				|| !"()Z".equals(mi.desc)) {
			return false;
		}
		n = nextMeaningful(n.getNext());
		if (!(n instanceof JumpInsnNode j2) || j2.getOpcode() != Opcodes.IFEQ) {
			return false;
		}
		return j2.label == fallFlyingIfeq.label;
	}

	private static int insertPassengerGateAfterFallFlyingCheckInClientPlayerTurn(MethodNode mn) {
		int inserted = 0;
		for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode mi)) {
				continue;
			}
			if (mi.getOpcode() != Opcodes.INVOKEVIRTUAL
					|| !"method_6128".equals(mi.name)
					|| !"()Z".equals(mi.desc)) {
				continue;
			}
			AbstractInsnNode next = nextMeaningful(mi.getNext());
			if (!(next instanceof JumpInsnNode jump) || jump.getOpcode() != Opcodes.IFEQ) {
				continue;
			}

			if (looksLikeAlreadyPatchedPassengerGateInClientPlayerTurn(jump)) {
				continue;
			}

			InsnList gate = new InsnList();
			gate.add(new VarInsnNode(Opcodes.ALOAD, 0));
			gate.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MC_ENTITY, "method_5782", "()Z", false));
			gate.add(new JumpInsnNode(Opcodes.IFEQ, jump.label));
			mn.instructions.insert(jump, gate);
			inserted++;
		}
		return inserted;
	}

	private static boolean looksLikeAlreadyPatchedPassengerGateInClientPlayerTurn(JumpInsnNode fallFlyingIfeq) {
		AbstractInsnNode n = nextMeaningful(fallFlyingIfeq.getNext());
		if (!(n instanceof VarInsnNode a0) || a0.getOpcode() != Opcodes.ALOAD || a0.var != 0) {
			return false;
		}
		n = nextMeaningful(n.getNext());
		if (!(n instanceof MethodInsnNode mi)
				|| mi.getOpcode() != Opcodes.INVOKEVIRTUAL
				|| !MC_ENTITY.equals(mi.owner)
				|| !"method_5782".equals(mi.name)
				|| !"()Z".equals(mi.desc)) {
			return false;
		}
		n = nextMeaningful(n.getNext());
		if (!(n instanceof JumpInsnNode j2) || j2.getOpcode() != Opcodes.IFEQ) {
			return false;
		}
		return j2.label == fallFlyingIfeq.label;
	}

	private static ClassNode readClass(byte[] bytes) {
		ClassReader cr = new ClassReader(bytes);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		return cn;
	}

	private static byte[] writeClass(ClassNode cn) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cn.accept(cw);
		return cw.toByteArray();
	}

	private static AbstractInsnNode previousMeaningful(AbstractInsnNode node) {
		AbstractInsnNode n = node;
		while (n != null && (n instanceof LabelNode || n instanceof LineNumberNode || n instanceof FrameNode)) {
			n = n.getPrevious();
		}
		return n;
	}

	private static AbstractInsnNode nextMeaningful(AbstractInsnNode node) {
		AbstractInsnNode n = node;
		while (n != null && (n instanceof LabelNode || n instanceof LineNumberNode || n instanceof FrameNode)) {
			n = n.getNext();
		}
		return n;
	}

	private static byte[] readAllBytes(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int read;
		while ((read = in.read(buf)) >= 0) {
			out.write(buf, 0, read);
		}
		return out.toByteArray();
	}

	private record PatchResult(byte[] bytes, int acosAbsInserted, int passengerGatesInserted) {}

	private static final class PatchReport {
		final String input;
		final String output;
		boolean foundGameRendererMixin;
		boolean foundClientPlayerMixin;
		int gameRendererAcosAbsInserted;
		int gameRendererPassengerGatesInserted;
		int clientPlayerPassengerGatesInserted;

		PatchReport(String input, String output) {
			this.input = input;
			this.output = output;
		}

		@Override
		public String toString() {
			return "CoolElytraDroneOnlyPatcher: " + input + " -> " + output + "\n"
					+ "  " + GAME_RENDERER_MIXIN + ": present=" + foundGameRendererMixin
					+ " acosAbsInserted=" + gameRendererAcosAbsInserted
					+ " passengerGatesInserted=" + gameRendererPassengerGatesInserted + "\n"
					+ "  " + CLIENT_PLAYER_MIXIN + ": present=" + foundClientPlayerMixin
					+ " passengerGatesInserted=" + clientPlayerPassengerGatesInserted;
		}
	}
}

