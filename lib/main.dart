import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const App());

const _ch = MethodChannel('com.mydev.dualwidget/ch');

class App extends StatelessWidget {
  const App({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Dual Widget',
    theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
    home: const Home(),
    debugShowCheckedModeBanner: false,
  );
}

class Album { final String id, name; Album(this.id, this.name); }
class AppInfo { final String name, pkg; AppInfo(this.name, this.pkg); }
class Folder {
  final String id, name; final List<String> apps;
  Folder({required this.id, required this.name, required this.apps});
  Map toJson() => {'id': id, 'name': name, 'apps': apps};
  factory Folder.from(Map j) => Folder(id: j['id'], name: j['name'], apps: List<String>.from(j['apps'] ?? []));
}

class Home extends StatefulWidget {
  const Home({super.key});
  @override State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> with SingleTickerProviderStateMixin {
  late TabController _tab;
  List<Album> albums = [];
  List<AppInfo> apps = [];
  List<Folder> folders = [];
  int photoCount = 0;
  String? selAlbum;
  int fr=26, fg=26, fb=46, fa=200;
  int nr=20, ng=20, nb=30, na=210, nsecs=5;
  bool loading = true;

  @override
  void initState() { super.initState(); _tab = TabController(length: 3, vsync: this); _load(); }
  @override
  void dispose() { _tab.dispose(); super.dispose(); }

  Future<void> _load() async {
    try {
      final raw = await _ch.invokeMethod('init') as String;
      final d = jsonDecode(raw) as Map;
      setState(() {
        albums = (d['albums'] as List).map((a) => Album(a['id'], a['name'])).toList();
        apps = (d['apps'] as List).map((a) => AppInfo(a['n'], a['p'])).toList();
        folders = (d['folders'] as List).map((f) => Folder.from(Map.from(f))).toList();
        photoCount = d['photoCount'] ?? 0;
        fr=d['fr']??26; fg=d['fg']??26; fb=d['fb']??46; fa=d['fa']??200;
        loading = false;
      });
    } catch (e) { setState(() => loading = false); }
  }

  Future<void> _save() => _ch.invokeMethod('saveFolders', {
    'folders': jsonEncode(folders.map((f) => f.toJson()).toList()),
    'a': fa, 'r': fr, 'g': fg, 'b': fb,
  });

  Future<void> _selAlbum(Album a) async {
    showDialog(context: context, barrierDismissible: false, builder: (_) => const AlertDialog(content: Row(children: [CircularProgressIndicator(), SizedBox(width: 16), Text('Loading...')])));
    try {
      final n = await _ch.invokeMethod('selectAlbum', {'id': a.id}) as int;
      if (mounted) Navigator.pop(context);
      setState(() { selAlbum = a.name; photoCount = n; });
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('${a.name}: $n photos ✓'), backgroundColor: Colors.green));
    } catch (e) { if (mounted) Navigator.pop(context); }
  }

  Future<void> _editFolder([Folder? f]) async {
    final r = await Navigator.push<Folder>(context, MaterialPageRoute(builder: (_) => EditPage(apps: apps, folder: f)));
    if (r != null) {
      setState(() {
        if (f == null) folders.add(r);
        else { final i = folders.indexWhere((x) => x.id == f.id); if (i >= 0) folders[i] = r; }
      });
      await _save();
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Dual Widget'),
      actions: [
        IconButton(icon: const Icon(Icons.photo_outlined), tooltip: 'Add Photo Widget', onPressed: () => _ch.invokeMethod('pinPhoto')),
        IconButton(icon: const Icon(Icons.folder_outlined), tooltip: 'Add Folder Widget', onPressed: () => _ch.invokeMethod('pinFolder')),
        IconButton(icon: const Icon(Icons.calculate_outlined), tooltip: 'Add Calculator Widget', onPressed: () => _ch.invokeMethod('pinCalc')),
        IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
      ],
      bottom: TabBar(controller: _tab, tabs: const [Tab(icon: Icon(Icons.photo), text: 'Photos'), Tab(icon: Icon(Icons.folder), text: 'Folders'), Tab(icon: Icon(Icons.notifications), text: 'Notif')]),
    ),
    body: loading ? const Center(child: CircularProgressIndicator())
        : TabBarView(controller: _tab, children: [_photoTab(), _folderTab(), _notifTab()]),
    floatingActionButton: ListenableBuilder(listenable: _tab, builder: (_, __) => _tab.index == 1
        ? FloatingActionButton.extended(onPressed: () => _editFolder(), icon: const Icon(Icons.create_new_folder_outlined), label: const Text('New Folder'))
        : const SizedBox.shrink()),
  );

  Widget _photoTab() => ListView(padding: const EdgeInsets.all(14), children: [
    Card(color: photoCount > 0 ? Colors.green.shade50 : Colors.orange.shade50, child: ListTile(
      leading: Icon(photoCount > 0 ? Icons.check_circle : Icons.photo_library_outlined, color: photoCount > 0 ? Colors.green : Colors.orange, size: 36),
      title: Text(photoCount > 0 ? '$photoCount photos active' : 'No photos yet', style: const TextStyle(fontWeight: FontWeight.bold)),
      subtitle: Text(photoCount > 0 ? 'Changes every 5 min${selAlbum != null ? " · $selAlbum" : ""}' : 'Select an album or add photos'),
    )),
    const SizedBox(height: 10),
    Row(children: [
      Expanded(child: FilledButton.icon(onPressed: () => _ch.invokeMethod('pinPhoto'), icon: const Icon(Icons.add_to_home_screen), label: const Text('Add to Home'))),
      const SizedBox(width: 8),
      Expanded(child: OutlinedButton.icon(onPressed: () async { await _ch.invokeMethod('pickPhotos'); await Future.delayed(const Duration(seconds: 2)); _load(); }, icon: const Icon(Icons.add_photo_alternate), label: const Text('Add Photos'))),
    ]),
    const SizedBox(height: 16),
    const Text('Albums', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
    const SizedBox(height: 8),
    albums.isEmpty
        ? const Center(child: Padding(padding: EdgeInsets.all(24), child: Column(children: [Icon(Icons.photo_library_outlined, size: 56, color: Colors.grey), SizedBox(height: 8), Text('No albums found', style: TextStyle(color: Colors.grey))])))
        : GridView.builder(
            shrinkWrap: true, physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 2, crossAxisSpacing: 8, mainAxisSpacing: 8, childAspectRatio: 1.2),
            itemCount: albums.length,
            itemBuilder: (_, i) {
              final a = albums[i];
              final sel = selAlbum == a.name;
              return GestureDetector(
                onTap: () => _selAlbum(a),
                child: Card(clipBehavior: Clip.antiAlias, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10), side: BorderSide(color: sel ? Colors.indigo : Colors.transparent, width: 3)), child: Stack(children: [
                  Container(color: Colors.indigo.shade50, child: const Center(child: Icon(Icons.photo_album, size: 48, color: Colors.indigo))),
                  Positioned(bottom: 0, left: 0, right: 0, child: Container(color: Colors.black54, padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6), child: Text(a.name, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold), maxLines: 1, overflow: TextOverflow.ellipsis))),
                  if (sel) Positioned(top: 6, right: 6, child: Container(decoration: const BoxDecoration(color: Colors.indigo, shape: BoxShape.circle), padding: const EdgeInsets.all(3), child: const Icon(Icons.check, color: Colors.white, size: 14))),
                ])),
              );
            }),
  ]);

  Widget _folderTab() => Column(children: [
    Expanded(child: folders.isEmpty
        ? const Center(child: Column(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.folder_open, size: 64, color: Colors.grey), SizedBox(height: 12), Text('No folders yet', style: TextStyle(color: Colors.grey)), Text('Tap + to create', style: TextStyle(color: Colors.grey))]))
        : ListView.builder(padding: const EdgeInsets.all(8), itemCount: folders.length, itemBuilder: (_, i) {
            final f = folders[i];
            return Card(child: ListTile(
              leading: const Icon(Icons.folder, size: 36, color: Colors.indigo),
              title: Text(f.name, style: const TextStyle(fontWeight: FontWeight.w600)),
              subtitle: Text('${f.apps.length} apps'),
              onTap: () => _editFolder(f),
              trailing: IconButton(icon: const Icon(Icons.delete_outline, color: Colors.red), onPressed: () { setState(() => folders.removeWhere((x) => x.id == f.id)); _save(); }),
            ));
          })),
    Card(margin: const EdgeInsets.all(8), child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('Widget Color & Opacity', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
      const SizedBox(height: 6),
      _slider('R', fr, Colors.red, (v) { setState(() => fr=v); _save(); }),
      _slider('G', fg, Colors.green, (v) { setState(() => fg=v); _save(); }),
      _slider('B', fb, Colors.blue, (v) { setState(() => fb=v); _save(); }),
      _slider('A', fa, Colors.grey, (v) { setState(() => fa=v); _save(); }),
      Container(height: 24, decoration: BoxDecoration(color: Color.fromARGB(fa,fr,fg,fb), borderRadius: BorderRadius.circular(6), border: Border.all(color: Colors.grey.shade300))),
    ]))),
  ]);

  Widget _slider(String label, int val, Color color, Function(int) onChanged) => Row(children: [
    SizedBox(width: 20, child: Text(label, style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 12))),
    Expanded(child: Slider(value: val.toDouble(), min: 0, max: 255, activeColor: color, onChanged: (v) => onChanged(v.toInt()))),
    SizedBox(width: 30, child: Text('$val', style: const TextStyle(fontSize: 11))),
  ]);
}

  Future<void> _saveNotif() => _ch.invokeMethod('saveNotif', {'a':na,'r':nr,'g':ng,'b':nb,'secs':nsecs});

  Widget _notifTab() => ListView(padding: const EdgeInsets.all(14), children: [
    const Text('Notification Overlay', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
    const SizedBox(height: 8),
    Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('Permissions Required', style: TextStyle(fontWeight: FontWeight.bold)),
      const SizedBox(height: 8),
      Row(children: [
        Expanded(child: FilledButton.icon(onPressed: () => _ch.invokeMethod('requestOverlay'), icon: const Icon(Icons.layers), label: const Text('Overlay Permission'))),
        const SizedBox(width: 8),
        Expanded(child: OutlinedButton.icon(onPressed: () => _ch.invokeMethod('requestNotifAccess'), icon: const Icon(Icons.notifications_active), label: const Text('Notif Access'))),
      ]),
    ]))),
    const SizedBox(height: 12),
    Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('Display Duration', style: TextStyle(fontWeight: FontWeight.bold)),
      Row(children: [
        const Text('3s'), Expanded(child: Slider(value: nsecs.toDouble(), min: 3, max: 10, divisions: 7, label: '${nsecs}s', onChanged: (v) { setState(() => nsecs=v.toInt()); _saveNotif(); })), const Text('10s'),
      ]),
      const SizedBox(height: 8),
      const Text('Overlay Color & Opacity', style: TextStyle(fontWeight: FontWeight.bold)),
      const SizedBox(height: 6),
      _sliderN('R', nr, Colors.red,   (v) { setState(() => nr=v); _saveNotif(); }),
      _sliderN('G', ng, Colors.green, (v) { setState(() => ng=v); _saveNotif(); }),
      _sliderN('B', nb, Colors.blue,  (v) { setState(() => nb=v); _saveNotif(); }),
      _sliderN('A', na, Colors.grey,  (v) { setState(() => na=v); _saveNotif(); }),
      const SizedBox(height: 8),
      Container(height: 40, decoration: BoxDecoration(color: Color.fromARGB(na,nr,ng,nb), borderRadius: BorderRadius.circular(8), border: Border.all(color: Colors.grey.shade300))),
      const SizedBox(height: 6),
      const Text('Preview of overlay background', style: TextStyle(fontSize: 11, color: Colors.grey)),
    ]))),
  ]);

  Widget _sliderN(String label, int val, Color color, Function(int) onChanged) => Row(children: [
    SizedBox(width: 20, child: Text(label, style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 12))),
    Expanded(child: Slider(value: val.toDouble(), min: 0, max: 255, activeColor: color, onChanged: (v) => onChanged(v.toInt()))),
    SizedBox(width: 30, child: Text('$val', style: const TextStyle(fontSize: 11))),
  ]);
}

class EditPage extends StatefulWidget {
  final List<AppInfo> apps; final Folder? folder;
  const EditPage({super.key, required this.apps, this.folder});
  @override State<EditPage> createState() => _EditState();
}

class _EditState extends State<EditPage> {
  late TextEditingController _ctrl;
  late Set<String> _sel;
  String _q = '';

  @override
  void initState() { super.initState(); _ctrl = TextEditingController(text: widget.folder?.name ?? ''); _sel = Set.from(widget.folder?.apps ?? []); }
  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  void _save() {
    final n = _ctrl.text.trim();
    if (n.isEmpty) { ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Enter folder name'))); return; }
    Navigator.pop(context, Folder(id: widget.folder?.id ?? '${DateTime.now().millisecondsSinceEpoch}', name: n, apps: _sel.toList()));
  }

  @override
  Widget build(BuildContext context) {
    final list = widget.apps.where((a) => a.name.toLowerCase().contains(_q.toLowerCase()) || a.pkg.toLowerCase().contains(_q.toLowerCase())).toList();
    return Scaffold(
      appBar: AppBar(title: Text(widget.folder == null ? 'New Folder' : 'Edit Folder'), actions: [TextButton.icon(onPressed: _save, icon: const Icon(Icons.check), label: const Text('Save'))]),
      body: Column(children: [
        Padding(padding: const EdgeInsets.fromLTRB(12,12,12,6), child: TextField(controller: _ctrl, decoration: const InputDecoration(labelText: 'Folder name', border: OutlineInputBorder(), prefixIcon: Icon(Icons.folder)))),
        Padding(padding: const EdgeInsets.fromLTRB(12,6,12,6), child: TextField(decoration: const InputDecoration(hintText: 'Search apps...', prefixIcon: Icon(Icons.search), border: OutlineInputBorder()), onChanged: (v) => setState(() => _q=v))),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4), child: Row(children: [
          Text('${_sel.length} selected', style: const TextStyle(color: Colors.indigo, fontWeight: FontWeight.w600)),
          const Spacer(),
          TextButton(onPressed: () => setState(() => _sel=widget.apps.map((a)=>a.pkg).toSet()), child: const Text('All')),
          TextButton(onPressed: () => setState(() => _sel.clear()), child: const Text('Clear')),
        ])),
        const Divider(height: 1),
        Expanded(child: ListView.builder(itemCount: list.length, itemBuilder: (_, i) {
          final a = list[i];
          return CheckboxListTile(title: Text(a.name), subtitle: Text(a.pkg, style: const TextStyle(fontSize: 10)), value: _sel.contains(a.pkg), onChanged: (v) => setState(() { if (v==true) _sel.add(a.pkg); else _sel.remove(a.pkg); }));
        })),
      ]),
    );
  }
}
