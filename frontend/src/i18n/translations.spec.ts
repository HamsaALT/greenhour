import da from './da.json';
import en from './en.json';

type Tree = { [key: string]: string | Tree };

function flatten(tree: Tree, prefix = ''): Map<string, string> {
  const result = new Map<string, string>();
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'string') result.set(path, value);
    else flatten(value, path).forEach((v, k) => result.set(k, v));
  }
  return result;
}

const placeholders = (text: string) =>
  [...text.matchAll(/{{\s*(\w+)\s*}}/g)].map((m) => m[1]).sort();

describe('translation files', () => {
  const danish = flatten(da as Tree);
  const english = flatten(en as Tree);

  it('have exactly the same keys', () => {
    expect([...danish.keys()].sort()).toEqual([...english.keys()].sort());
  });

  it('have no empty texts', () => {
    const empty = [...danish, ...english].filter(([, text]) => text.trim() === '');
    expect(empty).toEqual([]);
  });

  it('use the same placeholders in both languages', () => {
    for (const [key, text] of danish) {
      expect(placeholders(text), key).toEqual(placeholders(english.get(key) ?? ''));
    }
  });
});
