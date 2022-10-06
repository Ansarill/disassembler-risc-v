import java.util.*;
import java.io.*;
import java.nio.file.*;

public class Parser {

    public static final String[] regs = {"zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "s2", "s3", "s4", "s5",
            "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

    public static final String[] regsRVC = {"s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5"};

    public static final String unknwnCmd = "unknown_command\n";

    public static String toIndex(int index) {
        if (index == 0) {
            return "UNDEF";
        } else if (index >= Integer.parseInt("ff00", 16)) {
            if (index == Integer.parseInt("ff00", 16)) {
                return "BEFORE";
            } else if (index == Integer.parseInt("ff01", 16)) {
                return "AFTER";
            } else if (index <= Integer.parseInt("ff1f", 16)) {
                return "PROC";
            } else if (index >= Integer.parseInt("ff20", 16) && index <= Integer.parseInt("ff3f", 16)) {
                return "OS";
            } else if (index == Integer.parseInt("fff1", 16)) {
                return "ABS";
            } else if (index == Integer.parseInt("fff2", 16)) {
                return "COMMON";
            } else if (index == Integer.parseInt("ffff", 16)) {
                return "XINDEX";
            } else {
                return "RESERVE";
            }
        }
        return Integer.toString(index);
    }

    public static Map<Integer, String> sysRegs = new HashMap<Integer, String>();

    public static String defineSysRegs(int num) {
        if (num >= 3075 && num <= 3103) {
            return "hpmcounter" + (num - 3072);
        }
        if (num >= 3203 && num <= 3231) {
            return "hpmcounter" + (num - 3202) + "h";
        }
        if (num >= 928 && num <= 943) {
            return "pmpcfg" + (num - 928);
        }
        if (num >= 944 && num <= 1007) {
            return "pmpaddr" + (num - 944);
        }
        if (num >= 2819 && num <= 2847) {
            return "mhpmcounter" + (num - 2816);
        }
        if (num >= 2947 && num <= 2975) {
            return "mhpmcounter" + (num - 2944) + "h";
        }
        return sysRegs.get(num);
    }

    public static final String[] R_RV32I = {"add", "sll", "slt", "sltu", "xor", "srl", "or", "and"};

    public static final String[] RVC0 = {"c.addi4spn", "c.fld", "c.lw", "c.flw", unknwnCmd, "c.fsd", "c.sw", "c.fsw"};

    public static final String[] R_RV32M = {"mul", "mulh", "mulhsu", "mulhu", "div", "divu", "rem", "remu"};

    public static final String[] LOAD_RV32I = {"lb", "lh", "lw", unknwnCmd, "lbu", "lhu", unknwnCmd, unknwnCmd};

    public static final String[] STORE_RV32I = {"sb", "sh", "sw", unknwnCmd, unknwnCmd, unknwnCmd, unknwnCmd, unknwnCmd};

    public static final String[] I_RV32I = {"addi", "slli", "slti", "sltiu", "xori", unknwnCmd, "ori", "andi"};

    public static final String[] BRANCH_RV32I = {"beq", "bne", unknwnCmd, unknwnCmd, "blt", "bge", "bltu", "bgeu"};

    public static final String[] MEM_ORDER = {unknwnCmd, "csrrw", "csrrs", "csrrc", unknwnCmd, "csrrwi", "csrrsi", "csrrci"};


    public static long getInt(byte... bytes) {
        long res = 0, k = 0;
        for (byte one : bytes) {
            if (one < 0) {
                res += Math.pow(256, k) * (one + 256);
            } else {
                res += Math.pow(256, k) * one;
            }
            k++;
        }
        return res;
    }

    public static String getOperation(long inst, long offset_byte) {
        StringBuilder result = new StringBuilder("");
        long opcode = inst % 128;
        long rd = 0, rs1 = 0, rs2 = 0, funct7, funct3;
        if (opcode == 51) {
            rd = (inst >>> 7) % 32;
            rs1 = (inst >>> 15) % 32;
            rs2 = (inst >>> 20) % 32;
            funct7 = inst >>> 25;
            funct3 = (inst >>> 12) % 8;
            if (funct7 == 0) {
                return String.format("%s %s, %s, %s\n", R_RV32I[(int) funct3], regs[(int) rd], regs[(int) rs1], regs[(int) rs2]);
            } else if (funct7 == 32) {
                if (funct3 == 0) {
                    return String.format("%s %s, %s, %s\n", "sub", regs[(int) rd], regs[(int) rs1], regs[(int) rs2]);
                } else if (funct3 == 5) {
                    return String.format("%s %s, %s, %s\n", "sra", regs[(int) rd], regs[(int) rs1], regs[(int) rs2]);
                } else {
                    return unknwnCmd;
                }
            } else if (funct7 == 1) {
                return String.format("%s %s, %s, %s\n", R_RV32M[(int) funct3], regs[(int) rd], regs[(int) rs1], regs[(int) rs2]);
            } else {
                return unknwnCmd;
            }
        } else if (opcode == 19) {
            funct3 = (inst >>> 12) % 8;
            rd = (inst >>> 7) % 32;
            rs1 = (inst >>> 15) % 32;
            long imm = inst >>> 20;
            if (imm >= (int) Math.pow(2, 11)) {
                imm -= 2 * (int) Math.pow(2, 11);
            }
            if (funct3 == 1) {
                long shamt = imm % 32;
                return String.format("%s %s, %s, %s\n", I_RV32I[(int) funct3], regs[(int) rd], regs[(int) rs1], shamt);
            } else if (funct3 == 5) {
                long shamt = imm % 32;
                if (imm >>> 5 == 0) {
                    return String.format("%s %s, %s, %s\n", "srli", regs[(int) rd], regs[(int) rs1], shamt);
                } else if (imm >>> 5 == 32) {
                    return String.format("%s %s, %s, %s\n", "srai", regs[(int) rd], regs[(int) rs1], shamt);
                } else {
                    return unknwnCmd;
                }
            } else {
                return String.format("%s %s, %s, %s\n", I_RV32I[(int) funct3], regs[(int) rd], regs[(int) rs1], imm);
            }
        } else if (opcode == 3) {
            funct3 = (inst >>> 12) % 8;
            rd = (inst >>> 7) % 32;
            rs1 = (inst >>> 15) % 32;
            long imm = inst >>> 20;
            if (imm >= (int) Math.pow(2, 11)) {
                imm -= 2 * (int) Math.pow(2, 11);
            }
            return String.format("%s %s, %s(%s)\n", LOAD_RV32I[(int) funct3], regs[(int) rd], imm, regs[(int) rs1]);
        } else if (opcode == 35) {
            int imm = (int) (inst / (int) Math.pow(2, 25)) * 32 + (int) (inst / 128) % 32;
            if (imm >= 2048) {
                imm -= 4096;
            }
            rs1 = (inst / (int) Math.pow(2, 15)) % 32;
            rs2 = (inst / (int) Math.pow(2, 20)) % 32;
            funct3 = (inst / (int) Math.pow(2, 12)) % 8;
            return String.format("%s %s, %s(%s)\n", STORE_RV32I[(int) funct3], regs[(int) rs2], imm, regs[(int) rs1]);
        } else if (opcode == 99) {
            int imm = (int) ((inst >>> 31) * ((int) Math.pow(2, 12)) +
                    ((inst >>> 7) % 2) * (int) Math.pow(2, 11) +
                    ((inst >>> 25) % 64) * 32 +
                    ((inst >>> 8) % 16) * 2);
            if (imm >= (int) Math.pow(2, 12)) {
                imm -= 2 * (int) Math.pow(2, 12);
            }
            rs1 = (inst  >>> 15) % 32;
            rs2 = (inst >>> 20) % 32;
            funct3 = (inst >>> 12) % 8;
            if (symtab.containsKey(Integer.toHexString((int) offset_byte + imm))) {
                return String.format("%s %s, %s, %s\n",
                        BRANCH_RV32I[(int) funct3], regs[(int) rs1], regs[(int) rs2], imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
            } else {
                symtab.put(Integer.toHexString((int) offset_byte + imm), "LOC_" + String.format("%5s", Integer.toHexString((int) offset_byte + imm)).replace(" ", "0"));
                return String.format("%s %s, %s, %s\n",
                        BRANCH_RV32I[(int) funct3], regs[(int) rs1], regs[(int) rs2], imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
            }
        } else if (opcode == 103) {
            funct3 = (inst >>> 12) % 8;
            long imm = inst >>> 20;
            if (imm >= (int) Math.pow(2, 11)) {
                imm -= 2 * (int) Math.pow(2, 11);
            }
            rs1 = (inst >>> 15) % 32;
            rd = (inst  >>> 7) % 32;
            if (funct3 == 0) {
                return String.format("%s %s, %s(%s)\n", "jalr", regs[(int) rd], imm, regs[(int) rs1]);
            }
            return unknwnCmd;
        } else if (opcode == 111) {
            int imm = 2 * (int) ((inst >>> 31) * (int) Math.pow(2, 19) +
                    ((inst >>> 12) % 256) * (int) Math.pow(2, 11) +
                    ((inst >>> 20) % 2) * (int) Math.pow(2, 10) +
                    ((inst >>> 21) % 1024));
            if (imm >= (int) Math.pow(2, 20)) {
                imm -= 2 * (int) Math.pow(2, 20);
            }
            rd = (inst / 128) % 32;
            if (symtab.containsKey(Integer.toHexString((int) offset_byte + imm))) {
                return String.format("%s %s, %s\n", "jal", regs[(int) rd], imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
            } else {
                symtab.put(Integer.toHexString((int) offset_byte + imm), "LOC_" + String.format("%5s", Integer.toHexString((int) offset_byte + imm)).replace(" ", "0"));
                return String.format("%s %s, %s\n", "jal", regs[(int) rd], imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
            }
        } else if (opcode == 55) {
            long imm = (inst >>> 12);
            if (imm >= (long) Math.pow(2, 20)) {
                imm -= 2 * (long) Math.pow(2, 20);
            }
            rd = (inst >>> 7) % 32;
            return String.format("%s %s, %s\n", "lui", regs[(int) rd], imm);
        } else if (opcode == 23) {
            long imm = inst >>> 12;
            if (imm >= (long) Math.pow(2, 20)) {
                imm -= 2 * (long) Math.pow(2, 20);
            }
            rd = (inst >>> 7) % 32;
            return String.format("%s %s, %s\n", "auipc", regs[(int) rd], imm);
        } else if (opcode == 115) {
            rd = (inst >>> 7) % 32;
            funct3 = (inst >>> 12) % 8;
            long csr = inst >>> 20;
            if (funct3 == 0) {
                if (csr == 0 && rd == 0 && rs1 == 0) {
                    return String.format("%s\n", "ecall");
                } else if (csr == 1 && rd == 0 && rs1 == 0) {
                    return String.format("%s\n", "ebreak");
                } else {
                    return unknwnCmd;
                }
            } else if (funct3 < 4) {
                rs1 = (inst >>> 15) % 32;
                return String.format("%s %s, %s\n", MEM_ORDER[(int) funct3], defineSysRegs((int) rd), defineSysRegs((int) rs1)/*sysRegs[(int) rd], sysRegs[(int) rs1]*/);
            } else {
                int uimm = (int) (inst >>> 15) % 32;
                return String.format("%s %s, %s\n", MEM_ORDER[(int) funct3], defineSysRegs((int) rd), uimm);
            }
        }
        return unknwnCmd;
    }

    public static LinkedHashMap<String, String> symtab = new LinkedHashMap<String, String>();

    public static String getRVC(int inst, int offset_byte) {
        int opcode = inst % 4;
        int first = (inst >>> 13);
        int uimm = 0, imm = 0;
        if (opcode == 0) {
            int rs1 = (inst >>> 7) % 8;
            int rd = (inst % 32) / 4;
            if (first == 0) {
                uimm = 4 * ((inst >>> 6) % 2) + 8 * ((inst >>> 5) % 2) +
                        16 * ((inst >>> 11) % 4) + 64 * ((inst >>> 7) % 16);
                return String.format("%s %s, sp, %s\n", RVC0[first], regsRVC[rd], uimm);
            } else if (first == 1 || first == 5) {
                uimm = 8 * ((inst >>> 10) % 8) + 64 * ((inst >>> 5) % 4);
            } else if (first == 2 || first == 3 || first == 6 || first == 7) {
                uimm = 8 * ((inst >>> 10) % 8) + 64 * ((inst >>> 5) % 2) +
                        4 * ((inst >>> 6) % 2);
            } else if (first == 4) {
                return "reserved";
            }
            return String.format("%s %s, %s, %s\n", RVC0[first], regsRVC[rd], regsRVC[rs1], uimm);
        } else if (opcode == 1) {
            int rs1 = (inst >>> 7) % 32;
            if (first == 0) {
                imm = (inst >>> 2) % 32 + 32 * ((inst >>> 12) % 2);
                if (imm >= 32) {
                    imm -= 64;
                }
                if (rs1 == 0) {
                    return "c.nop";
                } else {
                    return String.format("%s %s, %s\n", "c.addi", regs[rs1], imm);
                }
            } else if (first == 1) {
                imm = (((inst >>> 3) % 8) * 2 +
                        ((inst >>> 11) % 2) * 16 +
                        ((inst >>> 2) % 2) * 32 +
                        ((inst >>> 7) % 2) * 64 +
                        ((inst >>> 6) % 2) * 128 +
                        ((inst >>> 9) % 4) * 256 +
                        ((inst >>> 8) % 2) * 1024 +
                        ((inst >>> 12) % 2) * 2048);
                if (imm >= 2048) {
                    imm -= 2 * 2048;
                }
                if (symtab.containsKey(Integer.toHexString((int) offset_byte + imm))) {
                    return String.format("%s %s\n", "c.jal", imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
                } else {
                    symtab.put(Integer.toHexString((int) offset_byte + imm), "LOC_" + String.format("%5s", Integer.toHexString((int) offset_byte + imm)).replace(" ", "0"));
                    return String.format("%s %s\n", "c.jal", imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
                }
            } else if (first == 2 && rs1 != 0) {
                imm = (inst >>> 2) % 32 + (inst >>> 11) % 2;
                if (imm >= 32) {
                    imm -= 64;
                }
                return String.format("%s %s, %s\n", "c.li", regs[rs1], imm);
            } else if (first == 3) {
                if (rs1 == 2) {
                    imm = 16 * ((inst >>> 6) % 2 + 2 * ((inst >>> 2) % 2) +
                            4 * ((inst >>> 5) % 2) + 8 * ((inst >>> 3) % 4) +
                            32 * ((inst >>> 12) % 2));
                    if (imm >= 512) {
                        imm -= 1024;
                    }
                    if (imm == 0) {
                        return unknwnCmd;
                    }
                    return String.format("%s %s, %s\n", "c.addi16sp", rs1, imm);
                } else if (rs1 != 0) {
                    imm = (int) Math.pow(2, 12) * ((inst >>> 2) % 32 + 32 * ((inst >>> 12) % 2));
                    if (imm >= (int) Math.pow(2, 17)) {
                        imm -= 2 * (int) Math.pow(2, 17);
                    }
                    return String.format("%s %s, %s\n", "c.lui", rs1, imm);
                } else {
                    return unknwnCmd;
                }
            } else if (first == 4) {
                imm = (inst >>> 2) % 16 + (inst >>> 12) % 2;
                int type = rs1 >>> 3;
                rs1 = rs1 % 8;
                if (type == 0) {
                    if (imm == 0) {
                        return String.format("%s %s, %s\n", "c.srli64", regsRVC[rs1], imm);
                    } else {
                        return String.format("%s %s, %s\n", "c.srli", regsRVC[rs1], imm);
                    }
                } else if (type == 1) {

                    if (imm == 0) {
                        return String.format("%s %s, %s\n", "c.srai64", regsRVC[rs1], imm);
                    } else {
                        return String.format("%s %s, %s\n", "c.srai", regsRVC[rs1], imm);
                    }
                } else if (type == 2) {
                    if (imm >= 32) {
                        imm -= 64;
                    }
                    return String.format("%s %s, %s\n", "c.andi", regsRVC[rs1], imm);
                } else if (type == 3) {
                    int f12 = (inst >>> 12) % 2;
                    int subtype = (inst >>> 5) % 4;
                    int rs2 = (inst >>> 2) % 8;
                    if (f12 == 0) {
                        if (subtype == 0) {
                            return String.format("%s %s, %s\n", "c.sub", regsRVC[rs1], regsRVC[rs2]);
                        } else if (subtype == 1) {
                            return String.format("%s %s, %s\n", "c.xor", regsRVC[rs1], regsRVC[rs2]);
                        } else if (subtype == 2) {
                            return String.format("%s %s, %s\n", "c.or", regsRVC[rs1], regsRVC[rs2]);
                        } else {
                            return String.format("%s %s, %s\n", "c.and", regsRVC[rs1], regsRVC[rs2]);
                        }
                    } else {
                        if (subtype == 0) {
                            return String.format("%s %s, %s\n", "c.subw", regsRVC[rs1], regsRVC[rs2]);
                        } else if (subtype == 1) {
                            return String.format("%s %s, %s\n", "c.addw", regsRVC[rs1], regsRVC[rs2]);
                        } else {
                            return "reserved";
                        }
                    }
                }
            } else if (first == 5) {
                imm = (2 * ((inst >>> 3) % 8) +
                        ((inst >>> 11) % 2) * 16 +
                        ((inst >>> 2) % 2) * 32 +
                        ((inst >>> 7) % 2) * 64 +
                        ((inst >>> 6) % 2) * 128 +
                        ((inst >>> 9) % 4) * 256 +
                        ((inst >>> 8) % 2) * 1024 +
                        ((inst >>> 12) % 2) * 2048);
                if (imm >= 2048) {
                    imm -= 2 * 2048;
                }
                if (symtab.containsKey(Integer.toHexString((int) offset_byte + imm))) {
                    return String.format("%s %s\n", "c.j", imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
                } else {
                    symtab.put(Integer.toHexString((int) offset_byte + imm), "LOC_" + String.format("%5s", Integer.toHexString((int) offset_byte + imm)).replace(" ", "0"));
                    return String.format("%s %s\n", "c.j", imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
                }
            } else if (first == 6) {
                rs1 = rs1 % 8;
                imm = 2 * ((inst >>> 3) % 4) + 8 * ((inst >>> 10) % 4) +
                        32 * ((inst >>> 2) % 2) + 64 * ((inst >>> 5) % 4) +
                        256 * ((inst >>> 12) % 2);
                if (imm >= 256) {
                    imm -= 2 * 256;
                }
                if (symtab.containsKey(Integer.toHexString((int) offset_byte + imm))) {
                    return String.format("%s %s, %s\n", "c.beqz", regsRVC[rs1], imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
                } else {
                    symtab.put(Integer.toHexString((int) offset_byte + imm), "LOC_" + String.format("%5s", Integer.toHexString((int) offset_byte + imm)).replace(" ", "0"));
                    return String.format("%s %s, %s\n", "c.beqz", regsRVC[rs1], imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
                }
            } else if (first == 7) {
                rs1 = rs1 % 8;
                imm = 2 * ((inst >>> 3) % 4) + 8 * ((inst >>> 10) % 4) +
                        32 * ((inst >>> 2) % 2) + 64 * ((inst >>> 5) % 4) +
                        256 * ((inst >>> 12) % 2);
                if (imm >= 256) {
                    imm -= 2 * 256;
                }
                if (symtab.containsKey(Integer.toHexString((int) offset_byte + imm))) {
                    return String.format("%s %s, %s\n", "c.bnez", regsRVC[rs1], imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
                } else {
                    symtab.put(Integer.toHexString((int) offset_byte + imm), "LOC_" + String.format("%5s", Integer.toHexString((int) offset_byte + imm)).replace(" ", "0"));
                    return String.format("%s %s, %s\n", "c.bnez", regsRVC[rs1], imm + " " + symtab.get(Integer.toHexString((int) offset_byte + imm)));
                }
            } else {
                return unknwnCmd;
            }
        } else if (opcode == 2) {
            int rd = (inst >>> 7) % 32;
            if (first == 0) {
                uimm = (inst >>> 2) % 16 + ((inst >>> 12) % 2) * 32;
                if (rd == 0) {
                    return unknwnCmd;
                }
                if (uimm == 0) {
                    return String.format("%s %s, %s\n", "c.slli64", regs[rd], uimm);
                } else {
                    return String.format("%s %s, %s\n", "c.slli", regs[rd], uimm);
                }
            } else if (first == 1) {
                uimm = 8 * ((inst >>> 5) % 4) + 32 * ((inst >>> 12) % 2) + 64 * ((inst >>> 2) % 8);
                return String.format("%s %s, %s\n", "c.fldsp", regs[rd], uimm);
            } else if (first == 2) {
                uimm = 4 * ((inst >>> 4) % 8) + 32 * ((inst >>> 12) % 2) + 64 * ((inst >>> 2) % 4);
                return String.format("%s %s, %s(sp)\n", "c.lwsp", regs[rd], uimm);
            } else if (first == 3) {
                uimm = 4 * ((inst >>> 4) % 8) + 32 * ((inst >>> 12) % 2) + 64 * ((inst >>> 2) % 4);
                return String.format("%s %s, %s(sp)\n", "c.flwsp", regs[rd], uimm);
            } else if (first == 4) {
                int f12 = (inst >>> 12) % 2;
                int rs2 = (inst >>> 2) % 32;
                if (f12 == 0) {
                    if (rd == 0) {
                        return unknwnCmd;
                    }
                    if (rs2 == 0) {
                        return String.format("%s %s\n", "c.jr", regs[rd]);
                    } else {
                        return String.format("%s %s, %s\n", "c.mv", regs[rd], regs[rs2]);
                    }
                } else {
                    if (rd == 0 && rs2 == 0) {
                        return String.format("%s\n", "c.ebreak", regs[rd]);
                    } else if (rd != 0 && rs2 == 0) {
                        return String.format("%s %s\n", "c.jalr", regs[rd]);
                    } else if (rd != 0 && rs2 != 0) {
                        return String.format("%s %s, %s\n", "c.add", regs[rd], regs[rs2]);
                    } else {
                        return unknwnCmd;
                    }
                }
            } else if (first == 5) {
                uimm = ((inst >>> 10) % 8) * 8 + ((inst >>> 7) % 8) * 64;
                int rs2 = (inst >>> 2) % 32;
                return String.format("%s %s, %s\n", "c.fsdsp", regs[rs2], uimm);
            } else if (first == 6) {
                uimm = ((inst >>> 9) % 16) * 4 + ((inst >>> 7) % 4) * 64;
                int rs2 = (inst >>> 2) % 32;
                return String.format("%s %s, %s(sp)\n", "c.swsp", regs[rs2], uimm);
            } else if (first == 7) {
                uimm = ((inst >>> 9) % 16) * 4 + ((inst >>> 7) % 4) * 64;
                int rs2 = (inst >>> 2) % 32;
                return String.format("%s %s, %s(sp)\n", "c.fswsp", regs[rs2], uimm);
            } else {
                return unknwnCmd;
            }

        }
        return unknwnCmd;
    }

    public static void main(String[] args) {
        try {
            Path inputFile = FileSystems.getDefault().getPath(args[0]);
            byte[] bytes = Files.readAllBytes(inputFile);
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(args[1]),
                            "utf-8"
                    )
            );
            if (bytes[0] != Integer.parseInt("7f", 16) ||
                    bytes[1] != Integer.parseInt("45", 16) ||
                    bytes[2] != Integer.parseInt("4c", 16) ||
                    bytes[3] != Integer.parseInt("46", 16)) {
                throw new IOException("Wrong file format");
            }
            sysRegs.putAll(Map.of(3072, "cycle", 3073, "time", 3074, "instret", 3200, "cycleh", 3201, "timeh",
                    3202, "instreth", 256, "sstatus", 260, "sie", 261, "stvec", 262, "scounteren"));
            sysRegs.putAll(Map.of(266, "senvcfg", 320, "sscratch", 321, "sepc", 322, "scause", 323, "stval",
                    324, "sip", 384, "satp", 1448, "scontext", 1536, "hstatus", 1538, "hedeleg"));
            sysRegs.putAll(Map.of(1539, "hideleg", 1540, "hie", 1542, "hcounteren", 1543, "hgeie", 1603, "htval",
                    1604, "hip", 1605, "hvip", 1610, "htinst", 3602, "hgeip", 1546, "henvcfg"));
            sysRegs.putAll(Map.of(1562, "henvcfgh", 1664, "hgatp", 1704, "hcontext", 1541, "htimedelta", 1557, "htimedeltah",
                    512, "vsstatus", 516, "vsie", 517, "vstvec", 576, "vsscratch", 577, "vsepc"));
            sysRegs.putAll(Map.of(578, "vscause", 579, "vstval", 580, "vsip", 640, "vsatp", 3857, "mvendorid", 3858, "marchid", 3859, "mimpid",
                    3860, "mhartid", 3861, "mconfigptr", 768, "mstatus"));
            sysRegs.putAll(Map.of(769, "misa", 770, "medeleg", 771, "mideleg", 772, "mie", 773, "mcounteren", 783, "mstatush", 832, "mscratch", 833, "mepc", 834, "mcause",
                    835, "mtval"));
            sysRegs.putAll(Map.of(843, "mtval2", 778, "menvcfg", 784, "menvcfgh", 1863, "mseccfg", 1879, "mseccfgh", 2816, "mcycle", 2818, "minstret", 2944, "mcycleh", 2946, "minstreth",
                    800, "mcountinhibit"));
            sysRegs.putAll(Map.of(1952, "tselect", 1953, "tdata1", 1954, "tdata2", 1955, "tdata3", 1959, "mcontext",
                    1969, "dpc", 1970, "dscratch0", 1971, "dscratch1", 836, "mip", 842, "mtinst"));
            sysRegs.put(1968, "dcsr");
            int e_shoff = (int) getInt(bytes[32], bytes[33], bytes[34], bytes[35]);
            int e_shnum = (int) getInt(bytes[48], bytes[49]);
            int e_shstrndx = (int) getInt(bytes[50], bytes[51]);
            int indOfOffset = 40 * e_shstrndx + e_shoff + 16;
            int sh_offset = (int) getInt(bytes[indOfOffset], bytes[indOfOffset + 1], bytes[indOfOffset + 2], bytes[indOfOffset + 3]);
            int sh_size = (int) getInt(bytes[indOfOffset + 4], bytes[indOfOffset + 5], bytes[indOfOffset + 6], bytes[indOfOffset + 7]);

            int indName = (e_shnum - 1) * 40 + e_shoff;
            int sh_name = (int) getInt(bytes[indName], bytes[indName + 1], bytes[indName + 2], bytes[indName + 3]);

            StringBuilder name = new StringBuilder("");
            int i = sh_offset + sh_name;
            while (bytes[i] != 0) {
                if (bytes[i] < 0) {
                    name.append((char) (bytes[i] + 256));
                } else {
                    name.append((char) bytes[i]);
                }
                i++;
            }

            if (!name.toString().equals(".shstrtab")) {
                throw new IOException("Wrong file format");
            }

            long text_offset = -1, text_size = -1, text_progBits = -1;
            long symtab_offset = -1, symtab_size = -1, symtab_progBits = -1;
            long strtab_offset = -1, strtab_size = -1, strtab_progBits = -1;
            for (i = 0; i < e_shnum - 1; i++) {
                int ind = e_shoff + 40 * i;
                long goToName = (int) getInt(bytes[ind], bytes[ind + 1], bytes[ind + 2], bytes[ind + 3]);
                long j = goToName + sh_offset;
                name = new StringBuilder("");
                while (bytes[(int) j] != 0) {
                    if (bytes[(int) j] < 0) {
                        name.append((char) (bytes[(int) j] + 256));
                    } else {
                        name.append((char) bytes[(int) j]);
                    }
                    j++;
                }
                if (name.toString().equals(".text")) {
                    text_offset = getInt(bytes[ind + 16], bytes[ind + 17], bytes[ind + 18], bytes[ind + 19]);
                    text_size = getInt(bytes[ind + 20], bytes[ind + 21], bytes[ind + 22], bytes[ind + 23]);
                    text_progBits = getInt(bytes[ind + 12], bytes[ind + 13], bytes[ind + 14], bytes[ind + 15]);
                } else if (name.toString().equals(".symtab")) {
                    symtab_offset = getInt(bytes[ind + 16], bytes[ind + 17], bytes[ind + 18], bytes[ind + 19]);
                    symtab_size = getInt(bytes[ind + 20], bytes[ind + 21], bytes[ind + 22], bytes[ind + 23]);
                    symtab_progBits = getInt(bytes[ind + 12], bytes[ind + 13], bytes[ind + 14], bytes[ind + 15]);
                } else if (name.toString().equals(".strtab")) {
                    strtab_offset = getInt(bytes[ind + 16], bytes[ind + 17], bytes[ind + 18], bytes[ind + 19]);
                    strtab_size = getInt(bytes[ind + 20], bytes[ind + 21], bytes[ind + 22], bytes[ind + 23]);
                    strtab_progBits = getInt(bytes[ind + 12], bytes[ind + 13], bytes[ind + 14], bytes[ind + 15]);
                }
            }

            int cnt = 0;
            i = (int) symtab_offset;
            while (i < symtab_offset + symtab_size) {
                long general = getInt(bytes[i + 12]);
                if (general % 16 == 2) {
                    long indInStr = getInt(bytes[i], bytes[i + 1], bytes[i + 2], bytes[i + 3]);
                    StringBuilder nameSymbol = new StringBuilder("");
                    int j = 0;
                    while (bytes[j + (int) strtab_offset + (int) indInStr] != 0) {
                        nameSymbol.append((char) bytes[j + (int) strtab_offset + (int) indInStr]);
                        j++;
                    }
                    long value = getInt(bytes[i + 4], bytes[i + 5], bytes[i + 6], bytes[i + 7]);
                    symtab.put(Long.toHexString(value), nameSymbol.toString());
                }
                i += 16;
            }

            StringBuilder instruction;
            StringBuilder codes;
            i = (int) text_offset;
            int one;
            long instruct;
            out.write(".text\n");
            while (i < text_offset + (int) text_size) {
                instruct = 0;
                one = bytes[i];
                instruction = new StringBuilder("");
                codes = new StringBuilder("");
                if (one % 4 == 3 || one % 4 == -1) {
                    for (int j = 3; j >= 0; j--) {
                        one = bytes[i + j];
                        if (one < 0) {
                            one += 256;
                        }
                        instruct = 256 * instruct + one;
                    }
                    getOperation(instruct, i - (int) text_offset + text_progBits);
                    i += 4;
                } else {
                    for (int j = 1; j >= 0; j--) {
                        one = bytes[i + j];
                        if (one < 0) {
                            one += 256;
                        }
                        instruct = 256 * instruct + one;

                    }
                    getRVC((int) instruct, (int) (i - text_offset + text_progBits));
                    i += 2;
                }
            }
            i = (int) text_offset;
            while (i < text_offset + (int) text_size) {
                instruct = 0;
                one = bytes[i];
                instruction = new StringBuilder("");
                codes = new StringBuilder("");
                if (one % 4 == 3 || one % 4 == -1) {
                    for (int j = 3; j >= 0; j--) {
                        one = bytes[i + j];
                        if (one < 0) {
                            one += 256;
                        }
                        instruct = 256 * instruct + one;
                        instruction.append(String.format("%8s", Integer.toBinaryString(one)).replace(' ', '0'));
                    }
                    if (symtab.containsKey(Integer.toHexString((int) (i - text_offset + text_progBits)))) {
                        out.write(String.format("%s %10s: %s",
                                String.format("%8s", Integer.toHexString((int) (i - text_offset + text_progBits))).replace(' ', '0'),
                                symtab.get(Integer.toHexString((int) (i - text_offset + text_progBits))),
                                getOperation(instruct, (int) (i - text_offset + text_progBits))));
                    } else {
                        out.write(String.format("%s %10s %s",
                                String.format("%8s", Integer.toHexString((int) (i - text_offset + text_progBits))).replace(' ', '0'),
                                " ",
                                getOperation(instruct, (int) (i - text_offset + text_progBits))));
                    }
                    i += 4;
                } else {
                    for (int j = 1; j >= 0; j--) {
                        one = bytes[i + j];
                        if (one < 0) {
                            one += 256;
                        }
                        instruct = 256 * instruct + one;
                        instruction.append(String.format("%8s", Integer.toBinaryString(one)).replace(' ', '0'));
                    }
                    if (symtab.containsKey(Integer.toHexString((int) (i - text_offset + text_progBits)))) {
                        out.write(String.format("%s %10s: %s",
                                String.format("%8s", Integer.toHexString((int) (i - text_offset + text_progBits))).replace(' ', '0'),
                                symtab.get(Integer.toHexString((int) (i - text_offset + text_progBits))),
                                getRVC((int) instruct, (int) (i - text_offset + text_progBits))));
                    } else {
                        out.write(String.format("%s %10s %s",
                                String.format("%8s", Integer.toHexString((int) (i - text_offset + text_progBits))).replace(' ', '0'),
                                " ",
                                getRVC((int) instruct, (int) (i - text_offset + text_progBits))));
                    }
                    i += 2;
                }
            }
            out.write("\n.symtab\n" + String.format("%s %-15s %7s %-8s %-8s %-8s %6s %s\n", "Symbol",
                    "Value", "Size", "Type", "Bind", "Vis", "Index", "Name"));
            int k = 0;
            cnt = 0;
            i = (int) symtab_offset;
            while (i < symtab_offset + symtab_size) {
                long indInStr = getInt(bytes[i], bytes[i + 1], bytes[i + 2], bytes[i + 3]);
                StringBuilder nameSymbol = new StringBuilder("");
                int j = 0;
                while (bytes[j + (int) strtab_offset + (int) indInStr] != 0) {
                    nameSymbol.append((char) bytes[j + (int) strtab_offset + (int) indInStr]);
                    j++;
                }
                long value = getInt(bytes[i + 4], bytes[i + 5], bytes[i + 6], bytes[i + 7]);
                long size = getInt(bytes[i + 8], bytes[i + 9], bytes[i + 10], bytes[i + 11]);
                long general = getInt(bytes[i + 12]);
                String type = "", bind = "";
                if (general / 16 == 0) {
                    type = "LOCAL";
                } else {
                    type = "GLOBAL";
                }
                if (general % 16 == 0) {
                    bind = "NOTYPE";
                } else if (general % 16 == 1) {
                    bind = "OBJECT";
                } else if (general % 16 == 2) {
                    bind = "FUNC";
                } else if (general % 16 == 3) {
                    bind = "SECTION";
                } else {
                    bind = "FILE";
                }
                String vis = "";
                if (getInt(bytes[i + 13]) == 0) {
                    vis = "DEFAULT";
                } else if (getInt(bytes[i + 13]) == 2) {
                    vis = "HIDDEN";
                }
                String index = toIndex((int) getInt(bytes[i + 14], bytes[i + 15]));
                out.write(String.format("[%4s] 0x%-15s %5s %-8s %-8s %-8s %6s %s\n", Integer.toHexString(cnt++),
                        Long.toHexString(value), Long.toString(size),
                        bind, type, vis, index, nameSymbol.toString()));
                i += 16;
            }
            out.close();
        } catch (InvalidPathException e) {
            System.out.println("Invalid Input File: " + e.getMessage());
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Input/Output Exception: " + e.getMessage());
        }
    }
}