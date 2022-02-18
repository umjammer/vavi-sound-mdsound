package MDSound.fmgen;

import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileNotFoundException;
import dotnet4j.io.FileShare;
import dotnet4j.io.FileStream;
import dotnet4j.io.SeekOrigin;

//	$Id: file.h,v 1.6 1999/11/26 10:14:09 cisc Exp $

    //#if !defined(win32_file_h)
    //#define win32_file_h

    //# include "types.h"

    public class FileIO
    {
        private Exception lastException = null;

        public enum Flags
        {
            Open (0x000001),
            Readonly (0x000002),
            Create (0x000004);
            int v;
            Flags(int v) { this.v = v; }
        };

        public enum SeekMethod
        {
            begin, current, end,
        };

        public enum Error
        {
            success (0),
            file_not_found (1),
            sharing_violation (2),
            unknown (-1);
            int v;
            Error(int v) { this.v = v; }
        };

        // ---------------------------------------------------------------------------
        //	構築/消滅
        // ---------------------------------------------------------------------------

        public FileIO()
        {
            flags = 0;
        }

        public FileIO(String filename, int flg/* = 0*/)
        {
            flags = 0;
            Open(filename, flg);
        }

        protected void finalize()
        {
            Close();
        }

        // ---------------------------------------------------------------------------
        //	ファイルを開く
        // ---------------------------------------------------------------------------

        public boolean Open(String filename, int flg/* = 0*/)
        {
            Close();

            path = filename;

            FileAccess access = ((flg & (int)Flags.Readonly.ordinal()) > 0) ? FileAccess.ReadWrite : FileAccess.ReadWrite;
            FileShare share = ((flg & (int)Flags.Readonly.ordinal()) > 0) ? FileShare.Read : FileShare.None;
            FileMode creation = ((flg & (int)Flags.Create.ordinal()) > 0) ? FileMode.Create : FileMode.Open;

            try
            {
                hfile = new FileStream(filename, creation, access, share);
            }
            catch (FileNotFoundException e)
            {
                lastException = e;
                error = Error.file_not_found;
                hfile = null;
            }
            catch (Exception e)
            {
                lastException = e;
                error = Error.unknown;
                hfile = null;
            }

            flags = (flg & (int)Flags.Readonly.ordinal()) | ((hfile == null) ? 0 : (int)(Flags.Open.ordinal()));
            //if ((flags & (int)Flags.Open)==0)
            //{
            //    switch (GetLastError())
            //    {
            //        case ERROR_FILE_NOT_FOUND: error = file_not_found; break;
            //        case ERROR_SHARING_VIOLATION: error = sharing_violation; break;
            //        default: error = unknown; break;
            //    }
            //}
            SetLogicalOrigin(0);

            return (flags & (int)Flags.Open.ordinal()) > 0;
        }

        // ---------------------------------------------------------------------------
        //	ファイルがない場合は作成
        // ---------------------------------------------------------------------------


        public boolean CreateNew(String filename)
        {
            Close();

            path = filename;

            FileAccess access = FileAccess.ReadWrite;
            FileShare share = FileShare.None;
            FileMode creation = FileMode.CreateNew;

            try
            {
                hfile = new FileStream(filename, creation, access, share);
            }
            catch (Exception e)
            {
                hfile = null;
            }

            flags = (hfile == null ? 0 : (int)(Flags.Open.ordinal()));
            SetLogicalOrigin(0);

            return (flags & (int)Flags.Open.ordinal()) > 0;
        }

        // ---------------------------------------------------------------------------
        //	ファイルを作り直す
        // ---------------------------------------------------------------------------
        public boolean Reopen(int flg/* = 0*/)
        {
            if ((flags & (int)Flags.Open.ordinal()) == 0) return false;
            if ((flags & (int)Flags.Readonly.ordinal()) > 0 && (flg & (int)Flags.Create.ordinal()) > 0) return false;

            if ((flags & (int)Flags.Readonly.ordinal()) > 0) flg |= (int)(Flags.Readonly.ordinal());

            Close();

            FileAccess access = ((flg & (int)Flags.Readonly.ordinal()) > 0) ? FileAccess.Read : FileAccess.ReadWrite;
            FileShare share = ((flg & (int)Flags.Readonly.ordinal()) > 0) ? FileShare.Read : FileShare.None;
            FileMode creation = ((flg & (int)Flags.Create.ordinal()) > 0) ? FileMode.Create : FileMode.Open;

            try
            {
                hfile = new FileStream(path, creation, access, share);
            }
            catch (Exception e)
            {
                hfile = null;
            }

            flags = (hfile == null ? 0 : (int)(Flags.Open.ordinal()));
            SetLogicalOrigin(0);

            return (flags & (int)Flags.Open.ordinal()) > 0;
        }

        // ---------------------------------------------------------------------------
        //	ファイルを閉じる
        // ---------------------------------------------------------------------------
        public void Close()
        {
            if ((GetFlags() & (int)Flags.Open.ordinal()) > 0)
            {
                hfile.close();
                flags = 0;
            }

        }

        public Error GetError()
        {
            return error;
        }

        // ---------------------------------------------------------------------------
        //	ファイル殻の読み出し
        // ---------------------------------------------------------------------------
        public int Read(byte[] dest, int len)
        {
            if ((GetFlags() & (int)Flags.Open.ordinal()) == 0)
                return -1;

            int readsize;
            if ((readsize = hfile.read(dest, 0, len)) == 0) return -1;

            return readsize;
        }

        // ---------------------------------------------------------------------------
        //	ファイルへの書き出し
        // ---------------------------------------------------------------------------
        public int Write(byte[] src, int len)
        {
            if (((GetFlags() & (int)Flags.Open.ordinal()) == 0) || ((GetFlags() & (int)Flags.Readonly.ordinal()) > 0))
                return -1;

            try
            {
                hfile.write(src, 0, len);
            }
            catch (Exception e)
            {
                return -1;
            }

            return len;
        }

        // ---------------------------------------------------------------------------
        //	ファイルをシーク
        // ---------------------------------------------------------------------------
        public boolean Seek(int fpos, SeekMethod method)
        {
            if ((GetFlags() & (int)Flags.Open.ordinal()) == 0)
                return false;

            SeekOrigin wmethod;
            switch (method)
            {
                case begin:
                    wmethod = SeekOrigin.Begin;
                    fpos += (int)lorigin;
                    break;
                case current:
                    wmethod = SeekOrigin.Current;
                    break;
                case end:
                    wmethod = SeekOrigin.End;
                    break;
                default:
                    return false;
            }

            try
            {
                hfile.seek(fpos, wmethod);
            }
            catch (Exception e)
            {
                return false;
            }

            return true;
        }

        // ---------------------------------------------------------------------------
        //	ファイルの位置を得る
        // ---------------------------------------------------------------------------
        public long Tellp()
        {
            if ((GetFlags() & (int)Flags.Open.ordinal()) == 0)
                return 0;

            return hfile.getPosition();
        }

        // ---------------------------------------------------------------------------
        //	現在の位置をファイルの終端とする
        // ---------------------------------------------------------------------------
        public boolean SetEndOfFile()
        {
            if ((GetFlags() & (int)Flags.Open.ordinal()) == 0)
                return false;

            //未サポート
            //return ::SetEndOfFile(hfile) != 0;

            return true;
        }

        public int GetFlags()
        {
            return flags;
        }

        public void SetLogicalOrigin(int origin)
        {
            lorigin = (int)origin;
        }

        private FileStream hfile;
        private int flags;
        private int lorigin;
        private Error error;
        private String path = "";//[ MAX_PATH];

        //FileIO(final FileIO&);
        //final FileIO& operator=(final FileIO&);

    };

    //#endif // 

