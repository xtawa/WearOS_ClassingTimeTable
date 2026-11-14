"""Windows-compatible syntax/config check. Not a Swift type checker or Xcode build."""
from pathlib import Path
import json
import sys
import tree_sitter_swift
from tree_sitter import Language, Parser
import yaml
ROOT = Path(__file__).resolve().parents[1]
parser = Parser(Language(tree_sitter_swift.language()))
errors = []
checked = 0
for path in sorted(ROOT.rglob('*.swift')):
    if any(part in path.parts for part in ['.build', '.tools', 'node_modules']): continue
    checked += 1
    source = path.read_bytes()
    tree = parser.parse(source)
    stack = [tree.root_node]
    while stack:
        node = stack.pop()
        if node.type == 'ERROR' or node.is_missing:
            errors.append(f'{path.relative_to(ROOT)}:{node.start_point.row+1}:{node.start_point.column+1}: {source[node.start_byte:node.end_byte][:100]!r}')
        stack.extend(node.children)
for path in ROOT.joinpath('Fixtures').glob('*.json'):
    json.loads(path.read_text(encoding='utf-8'))
for path in [ROOT/'project.yml', ROOT.parent/'.github/workflows/apple-ci.yml']:
    if path.exists(): yaml.safe_load(path.read_text(encoding='utf-8'))
if errors:
    print('\n'.join(errors)); sys.exit(1)
print(f'Swift syntax checked: {checked} files; fixture/config JSON/YAML valid. Native type checking requires Swift/Xcode.')
