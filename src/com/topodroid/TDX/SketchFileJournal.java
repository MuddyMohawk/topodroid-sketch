package com.topodroid.TDX;

import java.io.*;
import java.util.*;

/** Durable undo files for one metadata edit. The matching commit token lives in SQLite.
 * Callers serialize this with drawing saves. Files are confined to the survey directory. */
final class SketchFileJournal {
  final File directory, root;
  final String token;
  private final List<String[]> entries = new ArrayList<>();
  interface Hook { void afterFile(int index) throws IOException; }

  SketchFileJournal(File directory) throws IOException {
    this.directory = directory; root = directory.getParentFile().getCanonicalFile();
    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare sketch edit");
    File manifest = new File(directory,"manifest");
    if (manifest.exists()) {
      try (DataInputStream input = new DataInputStream(new FileInputStream(manifest))) {
        if (input.readInt() != 1) throw new IOException("Unsupported sketch edit journal");
        token = input.readUTF();
        int count = input.readInt();
        if (count < 0 || count > 100000) throw new IOException("Invalid sketch edit journal");
        for (int i=0;i<count;i++) {
          String source = input.readUTF(), target = input.readUTF();
          resolve(source); resolve(target); entries.add(new String[] {source,target});
        }
        if (input.read() != -1) throw new IOException("Invalid sketch edit journal");
      }
    } else token = UUID.randomUUID().toString();
  }
  private File resolve(String name) throws IOException {
    File file = new File(root,name).getCanonicalFile();
    if (name.isEmpty() || new File(name).isAbsolute() || name.contains("\\") ||
        !file.getPath().startsWith(root.getPath()+File.separator) ||
        Arrays.asList(name.split("/")).contains(".."))
      throw new IOException("Invalid sketch edit path");
    return file;
  }
  private String relative(File file) throws IOException {
    String path=file.getCanonicalPath();
    if (!path.startsWith(root.getPath()+File.separator)) throw new IOException("File is outside the survey directory");
    String name=path.substring(root.getPath().length()+1).replace(File.separatorChar,'/');
    resolve(name); return name;
  }
  static byte[] read(File file) throws IOException {
    try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[16384]; int count;
      while ((count=in.read(buffer)) != -1) out.write(buffer,0,count);
      return out.toByteArray();
    }
  }
  static void write(File file, byte[] bytes) throws IOException {
    try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); out.getFD().sync(); }
  }
  void stage(File source, File target, byte[] content) throws IOException {
    String from=relative(source), to=relative(target);
    if (!source.equals(target) && target.exists()) throw new IOException("A drawing file already uses the new name");
    int n = entries.size();
    write(new File(directory,n+".old"), read(source));
    write(new File(directory,n+".new"),content);
    entries.add(new String[]{from,to});
  }
  void seal() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
    out.writeInt(1); out.writeUTF(token); out.writeInt(entries.size());
    for (String[] pair : entries) { out.writeUTF(pair[0]); out.writeUTF(pair[1]); }
    write(new File(directory,"manifest.tmp"),bytes.toByteArray());
    if (!new File(directory,"manifest.tmp").renameTo(new File(directory,"manifest")))
      throw new IOException("Cannot record sketch edit");
  }
  void apply(Hook hook) throws IOException {
    for (int n=0;n<entries.size();n++) {
      String[] pair = entries.get(n); File source = resolve(pair[0]), target = resolve(pair[1]);
      remove(source);
      if (!new File(directory,n+".new").renameTo(target)) throw new IOException("Cannot replace drawing " + target.getName());
      if (hook != null) hook.afterFile(n);
    }
  }
  void recover(String committedToken) throws IOException {
    if (new File(directory,"manifest").exists() && !new File(directory,"rolledback").exists() && !token.equals(committedToken)) {
      for (int n=0;n<entries.size();n++) {
        String[] pair = entries.get(n); File source = resolve(pair[0]), target = resolve(pair[1]);
        File restore = new File(directory,n+".restore");
        write(restore,read(new File(directory,n+".old")));
        remove(target); if (!source.equals(target)) remove(source);
        if (!restore.renameTo(source)) throw new IOException("Cannot restore drawing " + source.getName());
      }
      write(new File(directory,"rolledback"),new byte[]{1});
    }
    cleanup();
  }
  private static void remove(File file) throws IOException {
    if (file.exists() && !file.delete()) throw new IOException("Cannot remove " + file.getName());
  }
  private void cleanup() throws IOException {
    File[] files = directory.listFiles();
    if (files == null) throw new IOException("Cannot read sketch edit journal");
    for (File file : files) {
      String name = file.getName();
      if (name.matches("[0-9]+\\.(old|new|restore)") || name.equals("manifest.tmp")) remove(file);
      else if (!name.equals("manifest") && !name.equals("rolledback")) throw new IOException("Unexpected sketch journal content");
    }
    remove(new File(directory,"manifest")); remove(new File(directory,"rolledback"));
    remove(directory);
  }
}
