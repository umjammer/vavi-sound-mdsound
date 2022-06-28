// $Id: file.h,v 1.6 1999/11/26 10:14:09 cisc Exp $

package mdsound.fmgen;

import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileNotFoundException;
import dotnet4j.io.FileShare;
import dotnet4j.io.FileStream;
import dotnet4j.io.SeekOrigin;


public class FileIO {
    private Exception lastException = null;

    public enum Flags {
        Open(0x000001),
        Readonly(0x000002),
        Create(0x000004);
        final int v;

        Flags(int v) {
            this.v = v;
        }
    }

    public enum SeekMethod {
        BEGIN, CURRENT, END,
    }

    public enum Error {
        success(0),
        file_not_found(1),
        sharing_violation(2),
        unknown(-1);
        final int v;

        Error(int v) {
            this.v = v;
        }
    }

    // 構築/消滅

    public FileIO() {
        flags = 0;
    }

    public FileIO(String filename, int flg/* = 0*/) {
        flags = 0;
        open(filename, flg);
    }

    protected void finalize() {
        close();
    }

    /**
     * ファイルを開く
     */
    public boolean open(String filename, int flg/* = 0*/) {
        close();

        path = filename;

        FileAccess access = ((flg & Flags.Readonly.v) > 0) ? FileAccess.ReadWrite : FileAccess.ReadWrite;
        FileShare share = ((flg & Flags.Readonly.v) > 0) ? FileShare.Read : FileShare.None;
        FileMode creation = ((flg & Flags.Create.v) > 0) ? FileMode.Create : FileMode.Open;

        try {
            hFile = new FileStream(filename, creation, access, share);
        } catch (FileNotFoundException e) {
            lastException = e;
            error = Error.file_not_found;
            hFile = null;
        } catch (Exception e) {
            lastException = e;
            error = Error.unknown;
            hFile = null;
        }

        flags = (flg & Flags.Readonly.v) | ((hFile == null) ? 0 : Flags.Open.v);
        //if ((flags & (int)Flags.Open)==0)
        //{
        //    switch (GetLastError())
        //    {
        //        case ERROR_FILE_NOT_FOUND: error = file_not_found; break;
        //        case ERROR_SHARING_VIOLATION: error = sharing_violation; break;
        //        default: error = unknown; break;
        //    }
        //}
        setLogicalOrigin(0);

        return (flags & Flags.Open.v) > 0;
    }

    /**
     * ファイルがない場合は作成
     */
    public boolean createNew(String filename) {
        close();

        path = filename;

        FileAccess access = FileAccess.ReadWrite;
        FileShare share = FileShare.None;
        FileMode creation = FileMode.CreateNew;

        try {
            hFile = new FileStream(filename, creation, access, share);
        } catch (Exception e) {
            hFile = null;
        }

        flags = (hFile == null ? 0 : Flags.Open.v);
        setLogicalOrigin(0);

        return (flags & Flags.Open.v) > 0;
    }

    /**
     * ファイルを作り直す
     */
    public boolean reopen(int flg/* = 0*/) {
        if ((flags & Flags.Open.v) == 0) return false;
        if ((flags & Flags.Readonly.v) > 0 && (flg & Flags.Create.v) > 0) return false;

        if ((flags & Flags.Readonly.v) > 0) flg |= Flags.Readonly.v;

        close();

        FileAccess access = ((flg & Flags.Readonly.v) > 0) ? FileAccess.Read : FileAccess.ReadWrite;
        FileShare share = ((flg & Flags.Readonly.v) > 0) ? FileShare.Read : FileShare.None;
        FileMode creation = ((flg & Flags.Create.v) > 0) ? FileMode.Create : FileMode.Open;

        try {
            hFile = new FileStream(path, creation, access, share);
        } catch (Exception e) {
            hFile = null;
        }

        flags = (hFile == null ? 0 : Flags.Open.v);
        setLogicalOrigin(0);

        return (flags & Flags.Open.v) > 0;
    }

    /**
     * ファイルを閉じる
     */
    public void close() {
        if ((getFlags() & Flags.Open.v) > 0) {
            hFile.close();
            flags = 0;
        }
    }

    public Error getError() {
        return error;
    }

    /**
     * ファイルからの読み出し
     */
    public int read(byte[] dest, int len) {
        if ((getFlags() & Flags.Open.v) == 0)
            return -1;

        int readsize;
        if ((readsize = hFile.read(dest, 0, len)) == 0) return -1;

        return readsize;
    }

    /**
     * ファイルへの書き出し
     */
    public int write(byte[] src, int len) {
        if (((getFlags() & Flags.Open.v) == 0) || ((getFlags() & Flags.Readonly.v) > 0))
            return -1;

        try {
            hFile.write(src, 0, len);
        } catch (Exception e) {
            return -1;
        }

        return len;
    }

    /**
     * ファイルをシーク
     */
    public boolean seek(int fpos, SeekMethod method) {
        if ((getFlags() & Flags.Open.v) == 0)
            return false;

        SeekOrigin wmethod;
        switch (method) {
        case BEGIN:
            wmethod = SeekOrigin.Begin;
            fpos += lOrigin;
            break;
        case CURRENT:
            wmethod = SeekOrigin.Current;
            break;
        case END:
            wmethod = SeekOrigin.End;
            break;
        default:
            return false;
        }

        try {
            hFile.seek(fpos, wmethod);
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * ファイルの位置を得る
     */
    public long tellp() {
        if ((getFlags() & Flags.Open.v) == 0)
            return 0;

        return hFile.getPosition();
    }

    /**
     * 現在の位置をファイルの終端とする
     */
    public boolean setEndOfFile() {
        if ((getFlags() & Flags.Open.v) == 0)
            return false;

        //未サポート
        //return ::SetEndOfFile(hfile) != 0;

        return true;
    }

    public int getFlags() {
        return flags;
    }

    public void setLogicalOrigin(int origin) {
        lOrigin = origin;
    }

    private FileStream hFile;
    private int flags;
    private int lOrigin;
    private Error error;
    private String path = "";
}