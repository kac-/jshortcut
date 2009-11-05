/***************************************************************************
 *   Copyright (C) 2009 by Piotr Kopeć                                     *
 *   piotr.kopec.ogolny@gmail.com                                          *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 ***************************************************************************/

package org.kac;

// based on http://www.i2s-lab.com/Papers/The_Windows_Shortcut_File_Format.pdf by Jesse Hager
import java.awt.BufferCapabilities.FlipContents;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.Map.Entry;

public class Shortcut {

	/* offset 0x00 */
	static final byte[] _headerEl = new byte[] { 0x4c, 0x00, 0x00, 0x00 };
	/* offset 0x04 */
	static final byte[] _headerGUID = new byte[] { 0x01, 0x14, 0x02, 0x00,
			0x00, 0x00, 0x00, 0x00,/* 0xC0 */-0x40, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x46 };

	/* flags */
	static final int F_ID_LIST = 1 << 0;
	static final int F_LOCATION = 1 << 1;
	static final int F_DESCRIPTION = 1 << 2;
	static final int F_RELATIVE_PATH = 1 << 3;
	static final int F_WORKING_DIRECTORY = 1 << 4;
	static final int F_COMMAND_LINE = 1 << 5;
	static final int F_CUSTOM_ICON = 1 << 6;
	static final int F_SOMETHING = 1 << 7;

	/* attributes */
	static final int A_READONLY = 1 << 0; // Target is read only.
	static final int A_HIDDEN = 1 << 1; // Target is hidden.
	static final int A_SYSTEM_FILE = 1 << 2; // Target is a system file.
	static final int A_VOLUME_LABES = 1 << 3; // Target is a volume label. (Not
	// possible)
	static final int A_DIRECTORY = 1 << 4; // Target is a directory.
	static final int A_MODIFIED = 1 << 5; // Target has been modified since last
	// backup. (archive)
	static final int A_ENCRYPTED = 1 << 6; // Target is encrypted (NTFS EFS)
	static final int A_NORMAL = 1 << 7; // Target is Normal??
	static final int A_TEMPORARY = 1 << 8; // Target is temporary.
	static final int A_SPARSE = 1 << 9; // Target is a sparse file.
	static final int A_REPARSE = 1 << 10; // Target has reparse point data.
	static final int A_COMPRESSED = 1 << 11; // Target is compressed.
	static final int A_OFFLINE = 1 << 12; // Target is offline.

	/* ShowWnd */
	static final int SW_HIDE = 0;
	static final int SW_NORMAL = 1;
	static final int SW_SHOWMINIMIZED = 2;
	static final int SW_SHOWMAXIMIZED = 3;
	static final int SW_SHOWNOACTIVATE = 4;
	static final int SW_SHOW = 5;
	static final int SW_MINIMIZE = 6;
	static final int SW_SHOWMINNOACTIVE = 7;
	static final int SW_SHOWNA = 8;
	static final int SW_RESTORE = 9;
	static final int SW_SHOWDEFAULT = 10;

	/* FileLocationInfo flags */
	static final int FF_LOCAL = 1 << 0;
	static final int FF_NETWORK = 1 << 1;

	/* LocalVolumeInfo type flags */
	static final int FLV_UNKNOWN = 0;// Unknown
	static final int FLV_NO_ROOT = 0;// No root directory
	static final int FLV_REMOVABLE = 0;// Removable (Floppy, Zip, etc..)
	static final int FLV_FIXED = 0;// Fixed (Hard disk)
	static final int FLV_REMOTE = 0;// Remote (Network drive)
	static final int FLV_CDROM = 0;// CD-ROM
	static final int FLV_RAM_DRIVE = 0;// Ram drive (Shortcuts to stuff on a ram
	// drive, now that’s smart...)

	/* fields */
	int flags = 0;
	int attributes = 0;
	long cTime = 0;
	long mTime = 0;
	long aTime = 0;
	int fileLength = 0;
	int iconID = 0;
	int showWnd = SW_NORMAL;
	int hotKey = 0;
	long unknown0 = 0;
	Vector<ShellItemID> shellItemIDList = null;
	FileLocationInfo fileLocationInfo = null;
	String description = null;
	String relativePath = null;
	String workingDirectory = null;
	String commandLine = null;
	String iconFileName = null;
	String something = null;
	int unknown1 = 0;

	static class ShellItemID {
		byte[] content = null;

		ShellItemID(byte[] content) {
			this.content = content;
			Charset.defaultCharset().name();
		}

		public String toString() {
			return "ShellItemId: " + hexDump(content)+"\n"+"ShellItemIdAscii: " + asciiDump(content);
		}
	}

	static class FileLocationInfo {
		/*
		 * all dwordThis is the total length of this structure and all following
		 * dataThis is a pointer to first offset after this structure. 1ChFlags
		 * Offset of local volume infoOffset of base pathname on local system
		 * Offset of network volume infoOffset of remaining pathname
		 */
		int length;
		int offset = 0x1C;
		int flags;
		int offLVI;
		int offLBP;
		int offNVI;
		int offRPN;
		LocalVolumeTable lvt = null;
		NetworkVolumeTable nvt = null;
		String remainingPathName = null;
		String basePathName = null;

		byte[] getBytes(){
			int size = 0;
			byte[] buff = null;
			try {
				byte[] bvt = null;
				byte[] bbpn = null;
				byte[] brpn = null;
				int iflag=0;
				int off=0;
				if(lvt!=null){
					iflag=FF_LOCAL;
					bvt = lvt.getBytes();
				}else if(nvt!=null){
					iflag=FF_NETWORK;
					bvt = nvt.getBytes();
				}
				else
					throw new MalformedShortcutException("no lvt or nvt");
				
				bbpn = (basePathName!=null)?basePathName.getBytes("US-ASCII"):null;
				brpn = (remainingPathName!=null)?remainingPathName.getBytes("US-ASCII"):null;
				int ibpn = (bbpn!=null)?bbpn.length+1:0;
				int irpn = (brpn!=null)?brpn.length+1:0;
				int ivt = bvt.length;
				size = 7*4 + ibpn + irpn + ivt;
				System.out.println(size);
				buff = new byte[size];
				setInt(buff, 0x00, size);
				setInt(buff, 0x04, 0x1C);
				setInt(buff, 0x08, iflag);
				setInt(buff, 0x0C, 0x1C);
				setInt(buff, 0x10, 0x1C+ivt);
				setInt(buff, 0x14, 0x1C);
				setInt(buff, 0x18, 0x1C+ivt+ibpn);
				//System.out.println(asciiDump(brpn));
				if(ivt>0)copyArray(buff, bvt, 0x1C);
				if(ibpn>0)copyArray(buff, bbpn, 0x1C+ivt);
				if(irpn>0)copyArray(buff, brpn, 0x1C+ivt+ibpn);
			}catch(Exception e){e.printStackTrace(); buff=null;}
			return buff;
		}
		String toString(String indent) {
			String ind = (indent == null) ? "" : indent;
			String ls = System.getProperty("line.separator");
			String s = "";
			s = "fileLocationInfo:" + ls + ind + "length:" + length + ls + ind
					+ "offset:" + offset + ls + ind + "flags:"
					+ (((flags & FF_LOCAL) > 0) ? "local;" : "")
					+ (((flags & FF_NETWORK) > 0) ? "network;" : "") + ls
					+ ((lvt != null) ? ind + lvt.toString(ind + ind) : "")
					+ ((nvt != null) ? ind + nvt.toString(ind + ind) : "");
			return s;
		}
	}
	
	
	static FileLocationInfo parseFileLocationInfo(byte[] buff, int offset)
			throws MalformedShortcutException {
		FileLocationInfo fli = new FileLocationInfo();
		fli.length = getInt(buff, offset + 0x00);
		fli.offset = getInt(buff, offset + 0x04);
		fli.flags = getInt(buff, offset + 0x08);
		if ((fli.flags & FF_LOCAL) > 0) {
			fli.offLVI = getInt(buff, offset + 0x0C);
			fli.offLBP = getInt(buff, offset + 0x10);
			LocalVolumeTable lvt = new LocalVolumeTable();
			lvt.length = getInt(buff, offset + 0x1C);
			lvt.type = getInt(buff, offset + 0x20);
			lvt.serial = getInt(buff, offset + 0x24);
			lvt.offVL = getInt(buff, offset + 0x28);
			lvt.volumeLabel = new String(subarray(buff, offset + 0x2C,
					lvt.length - 0x10 -1));
			fli.lvt = lvt;
			fli.basePathName = new String(subarray(buff, offset + fli.offLBP,
					fli.length - fli.offLBP-2));

		} else if ((fli.flags & FF_NETWORK) > 0) {
			fli.offNVI = getInt(buff, offset + 0x014);
			NetworkVolumeTable nvt = new NetworkVolumeTable();
			nvt.length = getInt(buff, offset + fli.offNVI);
			nvt.unknown0 = getInt(buff, offset + fli.offNVI + 0x04);
			nvt.offNS = getInt(buff, offset + fli.offNVI + 0x08);
			nvt.unknown1 = getInt(buff, offset + fli.offNVI + 0x0C);
			nvt.unknown2 = getInt(buff, offset + fli.offNVI + 0x10);
			nvt.shareName = new String(subarray(buff, offset + fli.offNVI
					+ nvt.offNS, nvt.length - 0x14-nvt.unknown0-1));
			if (nvt.unknown1 > 0) {
				nvt.localShareMapping = new String(subarray(buff, offset
						+ fli.offNVI + nvt.unknown1, nvt.unknown0-1));
			}
			fli.nvt = nvt;
		} else
			throw new MalformedShortcutException(
					"corrupted FileLocationInfo structure, field 'flag'==0");
		fli.offRPN = getInt(buff, offset + 0x18);
		fli.remainingPathName = new String(subarray(buff, offset + fli.offRPN,
				fli.length - fli.offRPN -1));
		return fli;
	}

	static class LocalVolumeTable {
		// all dword, label is asciz
		// Length of this structure.
		// Type of volume
		// Volume serial number
		// Offset of the volume name (Always 10h)
		// Volume label
		int length = 0;
		int type = 0;
		int serial = 0;
		int offVL = 0x10;
		String volumeLabel = null;

		

		byte[] getBytes() {
			int size = 0;
			byte[] buff = null;
			try {
				byte[] vl = (volumeLabel != null) ? volumeLabel
						.getBytes("US-ASCII") : null;
				int ivl = (vl != null) ? vl.length+1  : 0;
				size = 4 * 4 + ivl;
				buff = new byte[size];
				System.out.println(size);
				setInt(buff, 0x00, size);
				setInt(buff, 0x04, type);
				setInt(buff, 0x08, serial);
				setInt(buff, 0x0C, 0x10);
				if (ivl > 0) {
					copyArray(buff, vl, 0x10);
					buff[ivl - 1] = 0x00;
				}
								
			} catch (Exception e) {
				e.printStackTrace();
				buff = null;
			}
			return buff;
		}

		String toString(String indent) {
			String ind = (indent == null) ? "" : indent;
			String ls = System.getProperty("line.separator");
			String s = "";
			s = "LocalVolumeTable:" + ls + ind + "length:" + length + ls + ind
					+ "type:" + type + ls + ind + "serial:" + serial + ls + ind
					+ "offVL:" + offVL + ls
					+ ((volumeLabel != null) ? ind + volumeLabel + ls : "");
			return s;
		}
	}

	static class NetworkVolumeTable {
		// all dword, shareName is asciz
		// Length of this structure
		// Unknown, always 2h?
		// Offset of network share name (Always 14h)
		// Unknown, always zero?
		// Unknown, always 20000h?
		// Network share name
		int length;
		int unknown0 = 0x02;// in my its size of mapping string
		int offNS = 0x14;
		int unknown1 = 0x00;// in my its offset of mapping
		int unknown2 = 0x020000;// in my its 0x140000
		String shareName = null;
		String localShareMapping = null;

		byte[] getBytes() {
			int size = 0;
			byte[] buff = null;
			try {
				byte[] lsm = (localShareMapping != null) ? localShareMapping
						.getBytes("US-ASCII") : null;
				byte[] sn = (shareName != null) ? shareName
						.getBytes("US-ASCII") : null;
				int ilsm = (lsm != null) ? lsm.length+1 : 0;
				int isn = (sn != null) ? sn.length+1  : 0;
				size = 5 * 4 + ilsm + isn;
				buff = new byte[size];
				System.out.println(size);
				setInt(buff, 0x00, byteSize());
				setInt(buff, 0x04, ilsm);
				setInt(buff, 0x08, 0x14);
				setInt(buff, 0x0C, 0x14 + isn);
				setInt(buff, 0x10, 0x140000);
				if (isn > 0) {
					copyArray(buff, sn, 0x14);
					buff[isn - 1] = 0x00;
				}
				if (ilsm > 0) {
					copyArray(buff, lsm, 0x14 + isn);
					buff[0x14 + isn - 1] = 0x00;
				}
				
			} catch (Exception e) {
				e.printStackTrace();
				buff = null;
			}
			return buff;
		}

		int byteSize() {
			int size = 0;
			try {
				size = 5
						* 4
						+ ((shareName != null) ? shareName.getBytes("US-ASCII").length + 1
								: 0)
						+ ((localShareMapping != null) ? localShareMapping
								.getBytes("US-ASCII").length + 1 : 0);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return size;
		}

		String toString(String indent) {
			String ind = (indent == null) ? "" : indent;
			String ls = System.getProperty("line.separator");
			String s = "";
			s = "NetworkVolumeTable:"
					+ ls
					+ ind
					+ "length:"
					+ length
					+ ls
					+ ind
					+ "unknown0:"
					+ unknown0
					+ ls
					+ ind
					+ "offNS:"
					+ offNS
					+ ls
					+ ind
					+ "unknown1:"
					+ unknown1
					+ ls
					+ ind
					+ "unknown2:"
					+ unknown2
					+ ls
					+ ((shareName != null) ? ind + "shareName:" + shareName
							+ ls : "")
					+ ((localShareMapping != null) ? ind + "localShareMapping:"
							+ localShareMapping + ls : "");
			return s;
		}
	}

	public Shortcut() {
		super();
		shellItemIDList = new Vector<ShellItemID>();
		attributes =	A_NORMAL | A_OFFLINE;
	}
	public Shortcut(File relative) throws UnsupportedEncodingException{
		this();
		//this.relativePath = relative.getPath();
		this.workingDirectory = relative.getParent();
		shellItemIDList.add(new ShellItemID(shortToByte(_shit0CRoot0A)));
		shellItemIDList.add(new ShellItemID(createRootSHID(this.workingDirectory)));
		shellItemIDList.add(new ShellItemID(createFileSHID(relative.getName())));
		
	}
	public Shortcut(File relative, File working){
		this();
		this.relativePath = relative.getPath();
		this.workingDirectory = working.getPath();
	}
	public Shortcut(String relativePath){
		this();
		this.relativePath = relativePath;
		this.workingDirectory = new File(relativePath).getParent();
	}
	public Shortcut(String relativePath, String workingDir){
		this(relativePath);
		this.workingDirectory = workingDir;
	}

	static String describeFlags(int f) {
		String s = "";
		s = ((f & F_ID_LIST) > 0) ? s + "id_list;" : s;
		s = ((f & F_LOCATION) > 0) ? s + "F_LOCATION;" : s;
		s = ((f & F_DESCRIPTION) > 0) ? s + "description;" : s;
		s = ((f & F_RELATIVE_PATH) > 0) ? s + "relative_path;" : s;
		s = ((f & F_WORKING_DIRECTORY) > 0) ? s + "working_directory;" : s;
		s = ((f & F_COMMAND_LINE) > 0) ? s + "command_line;" : s;
		s = ((f & F_CUSTOM_ICON) > 0) ? s + "custom_icon;" : s;
		s = ((f & F_SOMETHING) > 0) ? s + "something;" : s;
		return s;
	}

	static String describeAttributes(int a) {
		String s = "";
		s = ((a & A_READONLY) > 0) ? s + "A_READONLY;" : s;
		s = ((a & A_HIDDEN) > 0) ? s + "A_HIDDEN;" : s;
		s = ((a & A_SYSTEM_FILE) > 0) ? s + "A_SYSTEM_FILE;" : s;
		s = ((a & A_VOLUME_LABES) > 0) ? s + "A_VOLUME_LABES;" : s;
		s = ((a & A_DIRECTORY) > 0) ? s + "A_DIRECTORY;" : s;
		s = ((a & A_MODIFIED) > 0) ? s + "A_MODIFIED;" : s;
		s = ((a & A_ENCRYPTED) > 0) ? s + "A_ENCRYPTED;" : s;
		s = ((a & A_NORMAL) > 0) ? s + "A_NORMAL;" : s;
		s = ((a & A_TEMPORARY) > 0) ? s + "A_TEMPORARY;" : s;
		s = ((a & A_SPARSE) > 0) ? s + "A_SPARSE;" : s;
		s = ((a & A_REPARSE) > 0) ? s + "A_REPARSE;" : s;
		s = ((a & A_COMPRESSED) > 0) ? s + "A_COMPRESSED;" : s;
		s = ((a & A_OFFLINE) > 0) ? s + "A_OFFLINE;" : s;
		return s;
	}

	static String describeShowWnd(int sw) {
		if (sw == SW_HIDE)
			return "SW_HIDE";
		else if (sw == SW_NORMAL)
			return "SW_NORMAL";
		else if (sw == SW_SHOWMINIMIZED)
			return "SW_SHOWMINIMIZED";
		else if (sw == SW_SHOWMAXIMIZED)
			return "SW_SHOWMAXIMIZED";
		else if (sw == SW_SHOWNOACTIVATE)
			return "SW_SHOWNOACTIVATE";
		else if (sw == SW_SHOW)
			return "SW_SHOW";
		else if (sw == SW_MINIMIZE)
			return "SW_MINIMIZE";
		else if (sw == SW_SHOWMINNOACTIVE)
			return "SW_SHOWMINNOACTIVE";
		else if (sw == SW_SHOWNA)
			return "SW_SHOWNA";
		else if (sw == SW_RESTORE)
			return "SW_RESTORE";
		else if (sw == SW_SHOWDEFAULT)
			return "SW_SHOWDEFAULT";
		else
			return "UNKNOWN(" + Integer.toString(sw) + ")";

	}

	static boolean byteMatch(byte[] pat, byte[] in, int idx) {
		if ((idx + pat.length) > in.length)
			return false;
		for (int i = 0; i < pat.length; i++)
			if (pat[i] != in[i + idx])
				return false;
			else
				;// System.out.printf("0x%x\n", in[idx+i]);
		return true;
	}

	static byte[] subarray(byte[] in, int idx, int len) {
		byte[] out = new byte[len];
		for (int i = 0; i < len; i++)
			out[i] = in[idx + i];
		return out;
	}

	static String hexDump(byte[] in) {
		String out = new String("");
		for (int i = 0; i < in.length; i++)
			if((i%8)==0&&i!=0)
				out += String.format("\n0x%02X, ", in[i] & 0xff);
			else
				out += String.format("0x%02X, ", in[i] & 0xff);
		return out;
	}

	static String asciiDump(byte[] in) {
		String out = new String("");
		for (int i = 0; i < in.length; i++)
			out += ((in[i] & 0xFF) == 0) ? '_' : (char) (in[i] & 0xFF);
		// out += (char);
		return out;
	}
	static int copyUString(byte[] out, byte[]in, int idx){
		setShort(out, idx, (short)(in.length/2));
		copyArray(out, in, idx+2);
		return in.length+2;
	}
	static int copyArray(byte[] out, byte[] in, int idx) {
		for (int i = in.length - 1; i >= 0; i--)
			out[idx + i] = in[i];
		return in.length;
	}

	static short getShort(byte[] in, int idx) {
		short out = 0;
		for (int i = 0; i < 2; i++)
			out |= ((short) in[idx + i] & 0xff) << i * 8;
		return out;
	}
	static int setShort(byte[] out, int idx, short val) {
		for (int i = 0; i < 2; i++)
			out[idx + i] = (byte) ((val >> i * 8) & 0xff);
		return 2;
	}

	static int getInt(byte[] in, int idx) {
		int out = 0;
		for (int i = 0; i < 4; i++)
			out |= ((int) in[idx + i] & 0xff) << i * 8;
		return out;
	}

	static int setInt(byte[] out, int idx, int val) {
		for (int i = 0; i < 4; i++)
			out[idx + i] = (byte) ((val >> i * 8) & 0xff);
		return 4;
	}

	static long getLong(byte[] in, int idx) {
		long out = 0;
		for (int i = 0; i < 8; i++)
			out |= ((long) in[idx + i] & 0xff) << i * 8;
		return out;
	}
	static int setLong(byte[] out, int idx, long val) {
		for (int i = 0; i < 8; i++)
			out[idx + i] = (byte) ((val >> i * 8) & 0xff);
		return 8;
	}


	static public Shortcut loadShortcut(File shortcutFile) throws IOException,
			MalformedShortcutException {
		Shortcut scut = new Shortcut();
		FileInputStream fis = new FileInputStream(shortcutFile);
		int len = fis.available();
		byte[] buff = new byte[len];
		int rlen = fis.read(buff);
		fis.close();
		if (rlen != len)
			throw new IOException("error ocurred while reading file: "
					+ shortcutFile.getAbsolutePath());
		if (!byteMatch(_headerEl, buff, 0x00))
			throw new MalformedShortcutException("bad header");
		if (!byteMatch(_headerGUID, buff, 0x04))
			throw new MalformedShortcutException("bad GUID");
		scut.flags = getInt(buff, 0x14);
		System.out.println(hexDump(subarray(buff, 0x14, 4)));
		// scut.flags = getInt(buff, 0x14);
		System.out.println(describeFlags(scut.flags));
		scut.attributes = getInt(buff, 0x18);
		System.out.println("attrib " + describeAttributes(scut.attributes));
		scut.cTime = getLong(buff, 0x1C);
		scut.mTime = getLong(buff, 0x24);
		scut.aTime = getLong(buff, 0x2C);
		scut.fileLength = getInt(buff, 0x34);
		scut.iconID = getInt(buff, 0x38);
		scut.showWnd = getInt(buff, 0x3C);
		scut.hotKey = getInt(buff, 0x40);
		scut.unknown0 = getLong(buff, 0x44);

		int offset = 0x4C;
		if ((scut.flags & F_ID_LIST) > 0) {
			len = getShort(buff, offset) & 0xFFFF;
			scut.shellItemIDList = parseShellItemIDList(buff, offset);
			System.out.println("shIdList len " + len);
			offset += len + 2;
			 for (ShellItemID shid : scut.shellItemIDList)
			 System.out.println(shid.toString());

		}
		if ((scut.flags & F_LOCATION) > 0) {
			len = getInt(buff, offset);
			scut.fileLocationInfo = parseFileLocationInfo(buff, offset);
			offset += len;
		}
		if ((scut.flags & F_DESCRIPTION) > 0) {
			len = getShort(buff, offset) & 0xFFFF;
			scut.description = new String(subarray(buff, offset + 2, len * 2),
					"UTF-16LE");
			System.out.println("description " + scut.description);
			offset += 2 * len + 2;
		}
		if ((scut.flags & F_RELATIVE_PATH) > 0) {
			len = getShort(buff, offset) & 0xFFFF;
			scut.relativePath = new String(subarray(buff, offset + 2, len * 2),
					"UTF-16LE");
			System.out.println("relative " + scut.relativePath);
			offset += 2 * len + 2;
		}
		if ((scut.flags & F_WORKING_DIRECTORY) > 0) {
			len = getShort(buff, offset) & 0xFFFF;
			scut.workingDirectory = new String(subarray(buff, offset + 2,
					len * 2), "UTF-16LE");
			System.out.println("working " + scut.workingDirectory);
			offset += 2 * len + 2;
		}
		if ((scut.flags & F_COMMAND_LINE) > 0) {
			len = getShort(buff, offset) & 0xFFFF;
			scut.commandLine = new String(subarray(buff, offset + 2, len * 2),
					"UTF-16LE");
			System.out.println("command line " + scut.commandLine);
			offset += 2 * len + 2;
		}
		if ((scut.flags & F_CUSTOM_ICON) > 0) {
			len = getShort(buff, offset) & 0xFFFF;
			scut.iconFileName = new String(subarray(buff, offset + 2, len * 2),
					"UTF-16LE");
			System.out.println("working " + scut.iconFileName);
			offset += 2 * len + 2;
		}
		if ((scut.flags & F_SOMETHING) > 0) {
			len = getShort(buff, offset) & 0xFFFF;
			System.out.println(len + " " + (buff.length - (offset + 2)));
			System.out.println(Integer.toHexString(offset));
			// System.out.println(Integer.toBinaryString(scut.flags));
			if (len > 0x10) {
				scut.something = new String(subarray(buff, offset + 0x10,
						len - 0x10));// buff.length-(offset+0x10+2)));//,
				// "UTF-16LE");
				System.out.println("something:" + scut.something);
			}
			offset += len + 2;
		}
		while (true) {
			len = getShort(buff, offset) & 0xFFFF;
			offset += len + 2;
			if (len == 0)
				break;
		}
		return scut;
	}

	static Vector<ShellItemID> parseShellItemIDList(byte[] buff, int off)
			throws MalformedShortcutException {
		Vector<ShellItemID> shv = new Vector<ShellItemID>();
		int tlen = getShort(buff, off) & 0xFFFF;
		int of = 2;
		while (true) {
			int len = getShort(buff, off + of) & 0xFFFF;
			if ((len + off) > buff.length)
				throw new MalformedShortcutException(
						"malformed ShiellItemIdList");
			if (len == 0) {
				if (of != tlen)
					throw new MalformedShortcutException(
							"malformed ShiellItemIdList");
				break;
			} else
				shv.add(new ShellItemID(subarray(buff, off + of+2, len-2)));
			of += len;
		}
		return shv;
	}

	

	
	int byteSize() {
		int size = 0;
		try {
			size = 7
					* 4
					+ 4
					* 8
					+ fileLocationInfo.getBytes().length
					+ ((description != null) ? description.getBytes("UTF-16LE").length
							: 0)
					+ ((relativePath != null) ? relativePath
							.getBytes("UTF-16LE").length : 0)
					+ ((workingDirectory != null) ? workingDirectory
							.getBytes("UTF-16LE").length : 0)
					+ ((commandLine != null) ? commandLine.getBytes("UTF-16LE").length
							: 0)
					+ ((iconFileName != null) ? iconFileName
							.getBytes("UTF-16LE").length : 0);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return size;
	}

	public String toString() {
		String s = "";
		String ls = System.getProperty("line.separator");
		s += "flags:"
				+ describeFlags(flags)
				+ ls
				+ "attr:"
				+ describeAttributes(attributes)
				+ ls
				+ "cTime:"
				+ new Date(cTime).toString()
				+ ls
				+ "mTime:"
				+ new Date(mTime).toString()
				+ ls
				+ "aTime:"
				+ new Date(aTime).toString()
				+ ls
				+ "fileLength:"
				+ fileLength
				+ ls
				+ "iconID:"
				+ iconID
				+ ls
				+ "showWnd:"
				+ describeShowWnd(showWnd)
				+ ls
				+ "hotKey:"
				+ hotKey
				+ ls
				+ "unknown0:"
				+ unknown0
				+ ls
				+ ((fileLocationInfo != null) ? fileLocationInfo.toString("\t")
						: "")
				+ ((description != null) ? "description:" + description + ls
						: "")
				+ ((relativePath != null) ? "relativePath:" + relativePath + ls
						: "")
				+ ((workingDirectory != null) ? "workingDirectory:"
						+ workingDirectory + ls : "")
				+ ((commandLine != null) ? "commandLine:" + commandLine + ls
						: "")
				+ ((iconFileName != null) ? "iconFileName:" + iconFileName + ls
						: "")
				+ ((something != null) ? "something:" + something + ls : "");
		return s;
	}
//

//
	byte[] getBytes(){
			byte[] buff = null;
			int size=0;
			int iflag=0;
			int off=0;
			try{
				byte[] bshell = null;
				byte[] bdesc = (description!=null)?description.getBytes("UTF-16LE"):null;
				byte[] brel = (relativePath!=null)?relativePath.getBytes("UTF-16LE"):null;
				byte[] bwork = (workingDirectory!=null)?workingDirectory.getBytes("UTF-16LE"):null;
				byte[] bcmd = (commandLine!=null)?commandLine.getBytes("UTF-16LE"):null;
				byte[] bico = (iconFileName!=null)?iconFileName.getBytes("UTF-16LE"):null;
				byte[] bfloc = (fileLocationInfo!=null)?fileLocationInfo.getBytes():null;
				int ishell = 0;
				if(shellItemIDList!=null){
					System.out.println("HAS SHELL ID LIST!!!!!!"+shellItemIDList);
					for(ShellItemID shi: shellItemIDList)
						ishell+=0x02+shi.content.length;
					
					ishell+=0x04;
					
					off = 2;
					bshell = new byte[ishell];
					setShort(bshell, 0, (short)(ishell-2));
					for(ShellItemID shi: shellItemIDList){
						setShort(bshell, off, (short)(shi.content.length+2));
						copyArray(bshell, shi.content, off+2);
						off+=2+shi.content.length;
						//	ishell+=0x02+shi.content.length;
					}
					setShort(bshell, off, (short)0x00);
					System.out.println("shi len "+off);
					System.out.println(commandLine);
				}
				size = 
					_headerEl.length
					+_headerGUID.length
					+7*4
					+4*8
					+ishell
					+((bfloc!=null)?bfloc.length:0)
					+((bdesc!=null)?bdesc.length+2:0)
					+((brel!=null)?brel.length+2:0)
					+((bwork!=null)?bwork.length+2:0)
					+((bcmd!=null)?bcmd.length+2:0)
					+((bico!=null)?bico.length+2:0)
					;
				iflag =
					((bshell!=null)?F_ID_LIST:0)
					|((bfloc!=null)?F_LOCATION:0)
					|((bdesc!=null)?F_DESCRIPTION:0)
					|((brel!=null)?F_RELATIVE_PATH:0)
					|((bwork!=null)?F_WORKING_DIRECTORY:0)
					|((bcmd!=null)?F_COMMAND_LINE:0)
					|((bico!=null)?F_CUSTOM_ICON:0)
					;
				iflag|=F_SOMETHING;
				System.out.println("write flags:"+describeFlags(iflag));
				off=0;
				buff = new byte[size];
				off+=copyArray(buff, _headerEl, off);
				off+=copyArray(buff, _headerGUID , off);
				off+=setInt(buff, off, iflag);
				off+=setInt(buff, off, attributes);
				off+=setLong(buff, off, cTime);
				off+=setLong(buff, off, mTime);
				off+=setLong(buff, off, aTime);
				off+=setInt(buff, off, fileLength);
				off+=setInt(buff, off, iconID);
				off+=setInt(buff, off, showWnd);
				off+=setInt(buff, off, hotKey);
				off+=setLong(buff, off, unknown0);
				
				off+=((bshell!=null)?copyArray(buff, bshell, off):0);
				off+=((bfloc!=null)?copyArray(buff, bfloc, off):0);
				off+=((bdesc!=null)?copyUString(buff, bdesc, off):0);
				off+=((brel!=null)?copyUString(buff, brel, off):0);
				off+=((bwork!=null)?copyUString(buff, bwork, off):0);
				
				off+=((bcmd!=null)?copyUString(buff, bcmd, off):0);
				off+=((bico!=null)?copyUString(buff, bico, off):0);
				//byte[] brel = (h!=null)?h.getBytes("UTF16-LE"):null;
			}catch(Exception e){
				e.printStackTrace();
				buff=null;
			}
			
			return buff;
	}

	static public byte[] createFileSHID(String name) throws UnsupportedEncodingException{
		byte[] out=null;
		//byte[] sa = name.getBytes("US-ASCII");
		byte[] sa = name.getBytes("windows-1250");
		byte[] su = name.getBytes("UTF-16LE");
		int size = 
			0x0C
			+sa.length +(((sa.length%2)==0)?2:1)
			+0x14
			+su.length + 2
			+2
			;
		size = 0x0C + sa.length+1;
		out = new byte[size];
		for(int i=(size-1); i>=0; i--)
			out[i] = 0;
		out[0] = (byte)0x32;
		copyArray(out, sa, 0x0C);
		
		return out;
	}
	static public byte[] createDirSHID(String name) throws UnsupportedEncodingException{
		byte[] out=null;
		//byte[] sa = name.getBytes("US-ASCII");
		byte[] sa = name.getBytes("windows-1250");
		int size = 0x0C + sa.length+1;
		out = new byte[size];
		for(int i=(size-1); i>=0; i--)
			out[i] = 0;
		out[0] = (byte)0x31;
		copyArray(out, sa, 0x0C);
		
		return out;
	}
	static public byte[] createRootSHID(String name) throws UnsupportedEncodingException{
		byte[] out=null;
		//byte[] sa = ("/"+name).getBytes("US-ASCII");
		byte[] sa = ("/"+name).getBytes("windows-1250");
		
		int size = sa.length+1;
		out = new byte[size];
		for(int i=(size-1); i>=0; i--)
			out[i] = 0;
		copyArray(out, sa, 0x00);
		return out;
	}

	
	static final short[] _shit0CRoot0A = new short[]{
		0x1F, 0x50, 0xE0, 0x4F, 0xD0, 0x20, 0xEA, 0x3A, 
		0x69, 0x10, 0xA2, 0xD8, 0x08, 0x00, 0x2B, 0x30, 
		0x30, 0x9D
	};

	static byte[] shortToByte(short[] in){
		byte[] out = new byte[in.length];
		for(int i= in.length-1;i>=0;i--)
			out[i] = (byte) (in[i]);//&0xFF);
		return out;
	}

	public static void main(String[] args) throws Exception {
		// for(String cs:Charset.availableCharsets().keySet())
		// System.out.println(cs);
		// if(true) return;
		// TODO Auto-generated method stub
		//new File(args[0]).new FileInputStream(args[0])
		//Shortcut scut = loadShortcut(new File(args[0]));
		
		//if(true)
		//	return;
		//System.out.println(scut.toString());
		//scut = new Shortcut();
		//scut.workingDirectory ="c:\\";
		//scut.relativePath = ".\\a.jar";
		/*
		scut.fileLocationInfo = new FileLocationInfo();
		scut.fileLocationInfo.lvt = new LocalVolumeTable();
		scut.fileLocationInfo.lvt.volumeLabel ="win";
		scut.fileLocationInfo.basePathName = "c:\\a.jar";
		scut.workingDirectory = "C:\\Documents and Settings\\Administrator\\Pulpit";
		*/
		
		
		//scut.something = null;
		//scut.workingDirectory = null;
		//scut.relativePath = null;
		Shortcut	scut = null;
		if(System.getProperty("os.name").toLowerCase().startsWith("win")){
			scut = new Shortcut(new File("c:\\totalcmd\\totalcmd.exe"));
		}else{
		 scut = loadShortcut(new File("/mnt/build/virt/gosc.txt.lnk"));
		}
		System.out.println(scut.toString());
		
		
		//scut.workingDirectory = "E:\\virt";
		//scut.relativePath ="c:\\program files\\k.exe";
		/*
		//scut.shellItemIDList = 
		scut.shellItemIDList.add(new ShellItemID(shortToByte(_shit0CRoot0A)));
		//scut.shellItemIDList.add(new ShellItemID(shortToByte(_shit0CRoot1B)));
		scut.shellItemIDList.add(new ShellItemID(createRootSHID("E:\\")));
		//scut.shellItemIDList.add(new ShellItemID(createDirSHID("program files")));
		scut.shellItemIDList.add(new ShellItemID(createDirSHID("virt")));
		scut.shellItemIDList.add(new ShellItemID(createFileSHID("k.exe")));
		scut.shellItemIDList.clear();
		//scut.shellItemIDList.add(new ShellItemID(shortToByte(_shit3Desktop2)));//new byte[0]));
		//System.out.println("OUT!!!!!!!!!!!!!!!!"+scut.shellItemIDList);
		//scut.commandLine = null;
		 */
		scut.shellItemIDList.clear();
		//scut.shellItemIDList.add(new ShellItemID(shortToByte(_shit0Unicode1)));
		scut.shellItemIDList.add(new ShellItemID(createFileSHID("gość.txt")));
		OutputStream os=null;
		if(System.getProperty("os.name").toLowerCase().startsWith("win")){
			os = new FileOutputStream("y.lnk");
		}
		else{
			os = new FileOutputStream("/mnt/build/virt/y.lnk");
		}
		os.write(scut.getBytes());
		os.flush();
		os.close();
		//scut = loadShortcut(new File("/mnt/build/virt/y.lnk"));
		
		//scut.getBytes();
		//System.out.println(asciiDump(scut.workingDirectory.getBytes("UTF-16LE")));
		// System.out.println(scut.toString());
	}

}
