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
            ShowReady();
        }

        void ShowReady()
        {
            step = Step.Ready;
            Btn desktopBox = null;
            Btn startBox = null;
            desktopBox = new Btn(Btn.Kind.Toggle, new RectangleF(56, 420, 200, 26), L.S("Masaüstü kısayolu", "Desktop shortcut"),
                delegate { desktop = !desktop; desktopBox.Checked = desktop; Invalidate(); });
            desktopBox.Checked = desktop;
            startBox = new Btn(Btn.Kind.Toggle, new RectangleF(262, 420, 240, 26), L.S("Başlat menüsü kısayolu", "Start menu shortcut"),
                delegate { startMenu = !startMenu; startBox.Checked = startMenu; Invalidate(); });
            startBox.Checked = startMenu;
            SetButtons(
                new Btn(Btn.Kind.Secondary, new RectangleF(520, 356, 96, 44), L.S("Değiştir", "Change"), ChooseFolder),
                desktopBox,
                startBox,
                new Btn(Btn.Kind.Primary, new RectangleF(636, 352, 128, 52), update ? L.S("GÜNCELLE", "UPDATE") : L.S("KUR", "INSTALL"), StartInstall),
                new Btn(Btn.Kind.Link, new RectangleF(590, 452, 174, 30), L.S("Gizlilik politikası", "Privacy policy"), OpenPrivacy));
        }

        /// <summary>Privacy policy on the website (DNZ collects nothing; see PRIVACY.md).</summary>
        static void OpenPrivacy()
        {
            try { Process.Start(new ProcessStartInfo("https://dnzclient.com/privacy") { UseShellExecute = true }); } catch { }
        }

        void ChooseFolder()
        {
            using (FolderBrowserDialog d = new FolderBrowserDialog())
            {
                d.Description = L.S("DNZ Launcher nereye kurulsun?", "Where should DNZ Launcher be installed?");
                d.SelectedPath = Directory.Exists(installDir) ? installDir : Programs();
                if (d.ShowDialog(this) == DialogResult.OK)
                {
                    string chosen = d.SelectedPath;
                    // A folder of its own, so uninstalling never removes anything else.
                    installDir = Path.GetFileName(chosen.TrimEnd('\\')) == "DNZ Launcher" ? chosen : Path.Combine(chosen, "DNZ Launcher");
                    Invalidate();
                }
            }
        }

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

        static void Link(string path, bool wanted, string target, string args, string icon)
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
                t.InvokeMember("Description", BindingFlags.SetProperty, null, link, new object[] { "DNZ Launcher" });
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
            // First install: the launcher starts in the language picked here (an existing setting is kept).
            string settings = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".dnzlauncher", "launcher.json");
            if (!File.Exists(settings))
            {
                Directory.CreateDirectory(Path.GetDirectoryName(settings));
                File.WriteAllText(settings, "{ \"language\": \"" + (L.Turkish ? "TR" : "EN") + "\" }");
            }
        }

        void ShowDone()
        {
            step = Step.Done;
            SetButtons(
                new Btn(Btn.Kind.Link, new RectangleF(520, 352, 96, 52), L.S("Kapat", "Close"), Close),
                new Btn(Btn.Kind.Primary, new RectangleF(626, 352, 138, 52), L.S("BAŞLAT", "LAUNCH"), delegate
                {
                    try
                    {
                        ProcessStartInfo psi = new ProcessStartInfo(JavaPath(installDir), JavaArgs());
                        psi.WorkingDirectory = installDir;
                        psi.UseShellExecute = false;
                        Process.Start(psi);
                    }
                    catch { }
                    Close();
                }));
        }

        void Fail(string message)
        {
            step = Step.Failed;
            error = message;
            SetButtons(new Btn(Btn.Kind.Primary, new RectangleF(626, 352, 138, 52), L.S("TEKRAR DENE", "TRY AGAIN"), ShowReady));
        }

        protected override bool CanClose()
        {
            return step != Step.Installing;
        }

        protected override void Relabel()
        {
            switch (step)
            {
                case Step.Ready: ShowReady(); break;
                case Step.Done: ShowDone(); break;
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
            Theme.Hero(g, new RectangleF(0, 0, W, 320));
            Theme.Draw(g, "DNZ Launcher Setup", Theme.F(12, 1), Color.FromArgb(170, 255, 255, 255), new RectangleF(18, 0, 300, 36), 0);

            // Logo and title.
            RectangleF logo = new RectangleF(56, 104, 104, 104);
            Theme.Fill(g, new RectangleF(logo.X - 4, logo.Y - 4, logo.Width + 8, logo.Height + 8), 26, Color.FromArgb(40, 255, 255, 255));
            Theme.DrawLogo(g, logo, 22);
            Theme.Draw(g, "DNZ Launcher", Theme.F(40, 2), Color.White, new RectangleF(186, 104, 560, 56), 0);
            Theme.Draw(g, L.S("Modern menüler, HUD, Zoom ve fazlası. Yüksek FPS için hazır.", "Modern menus, HUD, Zoom and more. Tuned for high FPS."),
                Theme.F(16, 0), Color.FromArgb(215, 255, 255, 255), new RectangleF(188, 160, 580, 28), 0);
            string pill = "v" + BuildInfo.Version;
            SizeF size = g.MeasureString(pill, Theme.F(12, 1));
            RectangleF pr = new RectangleF(190, 198, size.Width + 16, 24);
            Theme.Fill(g, pr, 12, Color.FromArgb(46, 255, 255, 255));
            Theme.Draw(g, pill, Theme.F(12, 1), Color.White, pr, 1);

            switch (step)
            {
                case Step.Ready:
                    Theme.Draw(g, L.S("Kurulum konumu", "Install location"), Theme.F(12, 1), Theme.Muted, new RectangleF(56, 326, 400, 22), 0);
                    RectangleF box = new RectangleF(56, 356, 452, 44);
                    Theme.Fill(g, box, 10, Theme.Surface);
                    Theme.Outline(g, box, 10, Theme.Border, 1f);
                    Theme.Draw(g, installDir, Theme.F(13, 0), Theme.Text, new RectangleF(box.X + 14, box.Y, box.Width - 28, box.Height), 0);
                    string note = update
                        ? L.S("Yüklü sürüm güncellenecek. Profillerin, dünyaların ve ayarların korunur.", "The installed version will be updated. Your profiles, worlds and settings are kept.")
                        : L.S("Yönetici izni gerekmez  •  Java dahil, ayrıca bir şey kurman gerekmez", "No admin rights needed  •  Java included, nothing else to install");
                    Theme.Draw(g, note, Theme.F(12, 0), Theme.Muted, new RectangleF(56, 456, 530, 24), 0);
                    break;

                case Step.Installing:
                    Theme.Draw(g, status, Theme.F(15, 1), Theme.Text, new RectangleF(56, 344, 560, 30), 0);
                    Theme.Draw(g, (int)Math.Round(shownProgress * 100) + "%", Theme.F(15, 2), Theme.Text, new RectangleF(600, 344, 164, 30), 2);
                    RectangleF track = new RectangleF(56, 384, 708, 12);
                    Theme.Fill(g, track, 6, Theme.SurfaceHigh);
                    float w = Math.Max(12f, track.Width * shownProgress);
                    RectangleF bar = new RectangleF(track.X, track.Y, w, track.Height);
                    using (LinearGradientBrush b = Theme.AccentBrush(track, Theme.Accent, Theme.Accent2)) Theme.Fill(g, bar, 6, b);
                    Theme.Draw(g, L.S("Birkaç saniye sürer, pencereyi kapatma.", "Takes a few seconds, keep this window open."), Theme.F(12, 0), Theme.Muted,
                        new RectangleF(56, 410, 708, 24), 0);
                    break;

                case Step.Done:
                    Check(g, new RectangleF(56, 356, 44, 44));
                    Theme.Draw(g, update ? L.S("Güncelleme tamamlandı", "Update complete") : L.S("Kurulum tamamlandı", "Setup complete"),
                        Theme.F(20, 2), Theme.Text, new RectangleF(114, 350, 400, 30), 0);
                    Theme.Draw(g, L.S("DNZ Launcher hazır. İyi oyunlar!", "DNZ Launcher is ready. Have fun!"), Theme.F(13, 0), Theme.Muted,
                        new RectangleF(114, 378, 400, 24), 0);
                    break;

                case Step.Failed:
                    Theme.Paragraph(g, error, Theme.F(14, 1), Theme.Danger, new RectangleF(56, 346, 550, 110));
                    break;
            }
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
