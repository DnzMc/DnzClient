// DNZ Launcher setup: installs for the current user (no admin rights), in the launcher's own look.
using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace Dnz
{
    class SetupForm : DnzForm
    {
        enum Step { Ready, Installing, Done, Failed }

        Step step = Step.Ready;
        string installDir = Path.Combine(Programs(), "DNZ Launcher");
        bool desktop = true;
        bool startMenu = true;
        readonly bool update;
        volatile float progress;
        float shownProgress;
        volatile string status = "";
        string error = "";

        public SetupForm() : base(820, 500, "DNZ Launcher Setup")
        {
            update = File.Exists(JavaPath(installDir));
            // No questions here: the files are put in place right away, then the launcher opens with its own
            // setup pages (language, look, privacy, profile, sign-in).
            SetButtons();
            Shown += delegate { StartInstall(); };
        }

        /// <summary>Left edge of the content on the right of the decoration.</summary>
        const float X = 330;

        void StartInstall()
        {
            if (LauncherRunning())
            {
                Fail(L.S("DNZ Launcher şu an açık. Sağ alttaki DNZ simgesine sağ tıklayıp Çıkış de, sonra tekrar dene.",
                    "DNZ Launcher is open. Right-click the DNZ icon at the bottom right, choose Quit, then try again."));
                return;
            }
            step = Step.Installing;
            progress = 0;
            shownProgress = 0;
            status = L.S("Hazırlanıyor...", "Preparing...");
            SetButtons();
            Thread t = new Thread(Install);
            t.IsBackground = true;
            t.Start();
        }

        void Install()
        {
            try
            {
                Directory.CreateDirectory(installDir);
                string root = Path.GetFullPath(installDir).TrimEnd('\\') + "\\";
                // An update replaces the program files only; profiles, worlds and settings live in the user folder.
                foreach (string old in new string[] { "lib", "runtime" })
                {
                    string dir = Path.Combine(installDir, old);
                    if (Directory.Exists(dir)) Directory.Delete(dir, true);
                }
                status = L.S("Dosyalar kopyalanıyor...", "Copying files...");
                using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload.zip"))
                using (ZipArchive zip = new ZipArchive(s, ZipArchiveMode.Read))
                {
                    long total = 0;
                    foreach (ZipArchiveEntry e in zip.Entries) total += e.Length;
                    long done = 0;
                    byte[] buffer = new byte[1 << 16];
                    foreach (ZipArchiveEntry e in zip.Entries)
                    {
                        string target = Path.GetFullPath(Path.Combine(installDir, e.FullName.Replace('/', '\\')));
                        if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase)) continue;
                        if (e.Name.Length == 0)
                        {
                            Directory.CreateDirectory(target);
                            continue;
                        }
                        Directory.CreateDirectory(Path.GetDirectoryName(target));
                        using (Stream input = e.Open())
                        using (FileStream output = File.Create(target))
                        {
                            int n;
                            while ((n = input.Read(buffer, 0, buffer.Length)) > 0)
                            {
                                output.Write(buffer, 0, n);
                                done += n;
                                progress = total > 0 ? 0.95f * done / total : 0.95f;
                            }
                        }
                    }
                }

                status = L.S("Kısayollar oluşturuluyor...", "Creating shortcuts...");
                string icon = Path.Combine(installDir, "dnz.ico");
                Link(DesktopLink(), desktop, JavaPath(installDir), JavaArgs(), icon);
                Link(StartMenuLink(), startMenu, JavaPath(installDir), JavaArgs(), icon);
                // Shortcuts of the old Workshop Uploader (earlier versions made them).
                foreach (string link in new string[] { UploaderDesktopLink(), UploaderStartMenuLink() })
                {
                    try { if (File.Exists(link)) File.Delete(link); } catch { }
                }
                Register(icon);
                progress = 1f;
                Thread.Sleep(350);
                BeginInvoke((MethodInvoker)ShowDone);
            }
            catch (Exception ex)
            {
                string message = ex.Message;
                BeginInvoke((MethodInvoker)delegate { Fail(L.S("Kurulum tamamlanamadı: ", "Setup could not finish: ") + message); });
            }
        }

        /// <summary>
        /// The launcher starts with the (signed) Java inside the install folder, so Windows' app control never blocks it.
        /// Working folder = install folder: the launcher finds its "lib" there (also for "start with Windows").
        /// </summary>
        static string JavaPath(string dir)
        {
            return Path.Combine(dir, "runtime\\bin\\javaw.exe");
        }

        static string JavaArgs()
        {
            return "-XX:+UseSerialGC -Xss2m -cp \"lib\\*\" dnz.launcher.MainKt";
        }

        static void Link(string path, bool wanted, string target, string args, string icon, string description = "DNZ Launcher")
        {
            // Not wanted: nothing is made (and a shortcut the player made themselves is never touched).
            if (!wanted) return;
            Type shellType = Type.GetTypeFromProgID("WScript.Shell");
            object shell = Activator.CreateInstance(shellType);
            try
            {
                object link = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { path });
                Type t = link.GetType();
                t.InvokeMember("TargetPath", BindingFlags.SetProperty, null, link, new object[] { target });
                t.InvokeMember("Arguments", BindingFlags.SetProperty, null, link, new object[] { args });
                t.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, link, new object[] { Path.GetFullPath(Path.Combine(Path.GetDirectoryName(target), "..\\..")) });
                t.InvokeMember("IconLocation", BindingFlags.SetProperty, null, link, new object[] { icon });
                t.InvokeMember("Description", BindingFlags.SetProperty, null, link, new object[] { description });
                t.InvokeMember("Save", BindingFlags.InvokeMethod, null, link, null);
                Marshal.ReleaseComObject(link);
            }
            finally
            {
                Marshal.ReleaseComObject(shell);
            }
        }

        /// <summary>Shows DNZ Launcher in Windows' "Installed apps" with its uninstaller.</summary>
        void Register(string icon)
        {
            long bytes = 0;
            foreach (string f in Directory.GetFiles(installDir, "*", SearchOption.AllDirectories)) bytes += new FileInfo(f).Length;
            using (RegistryKey k = Registry.CurrentUser.CreateSubKey(UninstallKey))
            {
                k.SetValue("DisplayName", "DNZ Launcher");
                k.SetValue("DisplayVersion", BuildInfo.Version);
                k.SetValue("Publisher", "DNZ");
                k.SetValue("DisplayIcon", icon);
                k.SetValue("InstallLocation", installDir);
                k.SetValue("UninstallString", "\"" + Path.Combine(installDir, "Uninstall DNZ Launcher.exe") + "\"");
                k.SetValue("EstimatedSize", (int)(bytes / 1024), RegistryValueKind.DWord);
                k.SetValue("NoModify", 1, RegistryValueKind.DWord);
                k.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                k.SetValue("InstallerLanguage", L.Turkish ? "TR" : "EN");
            }
        }

        void ShowDone()
        {
            step = Step.Done;
            SetButtons();
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(JavaPath(installDir), JavaArgs());
                psi.WorkingDirectory = installDir;
                psi.UseShellExecute = false;
                Process.Start(psi);
            }
            catch { }
            // A moment for the launcher window to come up, then this window goes.
            System.Windows.Forms.Timer close = new System.Windows.Forms.Timer();
            close.Interval = 1800;
            close.Tick += delegate { close.Stop(); Close(); };
            close.Start();
        }

        void Fail(string message)
        {
            step = Step.Failed;
            error = message;
            SetButtons(new Btn(Btn.Kind.Primary, new RectangleF(X, 410, 170, 52), L.S("Tekrar dene", "Try again"), StartInstall));
        }

        protected override bool CanClose()
        {
            return step != Step.Installing;
        }

        protected override void Relabel()
        {
            switch (step)
            {
                case Step.Done: SetButtons(); break;
                case Step.Failed: Fail(error); break;
                default: SetButtons(); break;
            }
        }

        protected override bool Tick()
        {
            if (step != Step.Installing) return false;
            shownProgress += (progress - shownProgress) * 0.2f;
            return true;
        }

        protected override void PaintContent(Graphics g)
        {
            SidePanel(g);
            switch (step)
            {
                case Step.Ready:
                case Step.Installing:
                case Step.Done:
                    bool done = step == Step.Done;
                    Theme.Draw(g, done ? L.S("Hazır!", "Ready!") : update ? L.S("Güncelleniyor", "Updating") : L.S("Hazırlanıyor", "Getting ready"),
                        Theme.F(28, 2), Theme.Text, new RectangleF(X, 150, 460, 48), 0);
                    string pct = (int)Math.Round((done ? 1f : shownProgress) * 100) + "%";
                    Theme.Draw(g, done ? L.S("DNZ Launcher açılıyor...", "Opening DNZ Launcher...") : status, Theme.F(13, 0), Theme.Muted,
                        new RectangleF(X, 200, 380, 24), 0);
                    Theme.Draw(g, pct, Theme.F(13, 2), Theme.Accent, new RectangleF(X + 380, 200, 80, 24), 2);
                    RectangleF track = new RectangleF(X, 236, 460, 10);
                    Theme.Fill(g, track, 5, Theme.SurfaceHigh);
                    Theme.Fill(g, new RectangleF(track.X, track.Y, Math.Max(10f, track.Width * (done ? 1f : shownProgress)), track.Height), 5, Theme.Accent);
                    Theme.Draw(g, L.S("Birkaç saniye sürer. Sonra kurulum sayfaları açılır.", "Takes a few seconds. Then the setup pages open."),
                        Theme.F(12, 0), Theme.Muted, new RectangleF(X, 262, 460, 24), 0);
                    break;

                case Step.Failed:
                    Theme.Draw(g, L.S("Kurulum yapılamadı", "Setup failed"), Theme.F(28, 2), Theme.Text, new RectangleF(X, 56, 460, 48), 0);
                    Theme.Paragraph(g, error, Theme.F(13, 1), Theme.Danger, new RectangleF(X, 116, 460, 200));
                    break;
            }
        }

        /// <summary>Left panel like the launcher's setup pages: the app icon and its name.</summary>
        void SidePanel(Graphics g)
        {
            const float panel = 290;
            g.FillRectangle(new SolidBrush(Theme.Rgb(0x0D1118)), 0, 0, panel, H);
            using (Pen edge = new Pen(Theme.Border, 1f)) g.DrawLine(edge, panel, 0, panel, H);
            Theme.DrawLogo(g, new RectangleF(32, 44, 40, 40), 10);
            Theme.Draw(g, "DNZ Launcher", Theme.F(16, 2), Theme.Text, new RectangleF(84, 44, 190, 40), 0);
            Theme.Draw(g, "v" + BuildInfo.Version, Theme.F(12, 0), Theme.Muted, new RectangleF(32, H - 56, 220, 24), 0);
        }

        static void Check(Graphics g, RectangleF r)
        {
            Theme.Fill(g, r, r.Width / 2, Theme.Alpha(Theme.Success, 40));
            using (Pen p = new Pen(Theme.Success, 3.2f))
            {
                p.StartCap = LineCap.Round;
                p.EndCap = LineCap.Round;
                p.LineJoin = LineJoin.Round;
                g.DrawLines(p, new PointF[] { new PointF(r.X + 13, r.Y + 23), new PointF(r.X + 19.5f, r.Y + 29.5f), new PointF(r.X + 31.5f, r.Y + 15.5f) });
            }
        }

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new SetupForm());
        }
    }
}
