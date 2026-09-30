#!/usr/bin/env python3
"""The showcase bank (r108): the parallax pages the store listings and the presenter's video show.

  tools/showcase_pages.py [OUT_DIR]        writes the pages and their round.json (default demo/showcase)
  tools/parallax-lab-shots.sh demo/showcase demo/build/showcase        renders them (stills + contact.png)
  ./gradlew :demo:lab --args="demo/showcase"                           shows them scrolling, to grade

Each page is a .jplax (what browser games and Godot load, and what the editor opens to export a .plax). Its atlas stays
where it is in the repository: round.json's atlasDir says where; a game copies the atlas next to the page.
Built from the round 1 grades (demo/lab/round1): Simon's pages that graded 3-4 are kept as they are or completed (a
white lower half filled, a flat speed span stretched); the atlases no page used get a page written with the
parallax-pages skill.
"""
import copy
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from parallax_lab import ROOT, layer, layers, lint, load, page_of, set_speeds  # noqa: E402


def first_of_each(layer_list):
	"""The first layer of each region: Calm draws most of its regions twice, at two depths."""
	seen = set()
	return [l for l in layer_list if not (l['regionName'] in seen or seen.add(l['regionName']))]


def calm_day():
	"""Simon's Calm, as saved (round 1: 3; its white-sky variant 4)."""
	return load('editor/Files/transfer/calm.plaxpj'), 'editor/Files/transfer'


def calm_sunset():
	"""Calm's layers on boss.atlas, the same scene in silhouette, one of each and lowered: a sunset sky needs room."""
	page, _ = calm_day()
	page['pageModel']['atlasName'] = 'boss.atlas'
	kept = first_of_each(layers(page))
	for l in kept:
		l['decal_Y_Ratio'] = max(-4.0, l['decal_Y_Ratio'] - 22)
	page['pageModel']['pageList'] = kept
	colours = page_of('', [], sky=('2b1640', 'ff9a4d'), ground=('140709', '0a0304'))
	for key in ('topHalf_top', 'topHalf_bottom', 'bottomHalf_top', 'bottomHalf_bottom'):
		page[key] = colours[key]
	return page, 'editor/Files/transfer'


def meadow():
	"""pure.atlas: Calm's far scene, one layer of each, with the foreground strips only that atlas has in front."""
	page, _ = calm_day()
	far = first_of_each(layers(page))
	# Lift the far scene so the foreground has room below it.
	for l in far:
		l['decal_Y_Ratio'] += 18
	# Without its twin below it, Trees_close's bottom edge showed over the sky's pale end: tuck it behind Trees_far.
	next(l for l in far if l['regionName'] == 'Trees_close')['decal_Y_Ratio'] -= 7
	# The front continues the far scene's speeds at x1.2: the span stays under the skill's ~15x.
	speed = far[-1]['parallaxScalingSpeedX']
	front = []
	for i, (region, size, dx, dy) in enumerate([('feuille_fond', 1.0, 15, 14), ('grass_back', 1.0, 45, 10),
	                                           ('feuille_vert', 1.1, 5, 4), ('grass', 1.2, 60, -6)]):
		front.append(layer(region, speed=speed * 1.2 ** (i + 1), size=size, dx=dx, dy=dy))
	page['pageModel']['pageList'] = far + front
	page['pageModel']['atlasName'] = 'pure.atlas'
	# Gaps under the lifted far scene show the lower gradient: make it the forest's green, not the editor's white.
	colours = page_of('', [], ground=('1e6b55', '1e6b55'))
	page['bottomHalf_top'], page['bottomHalf_bottom'] = colours['bottomHalf_top'], colours['bottomHalf_bottom']
	return page, 'editor/Files/Demos/Day/pureTest'


def one_night():
	"""Simon's OneNight, as saved (round 1: 3)."""
	return load('editor/Files/Demos/OneNight.plaxpj'), 'editor/Files/Demos'


def purple_fairy():
	"""Simon's PurpleFairy, as saved (round 1: 3)."""
	return load('editor/Files/Demos/PurpleFairy.plaxpj'), 'editor/Files/Demos'


def forest():
	"""Simon's calmTree, as saved: never graded (not in round 1)."""
	return load('editor/Files/Aa new/calmTree.plaxpj'), 'editor/Files/Aa new'


def lowered(path, shift, sky, ground, span=None):
	"""A page whose layers leave the lower part white (a game draws its ground there), moved down to fill the screen."""
	page = load(path)
	for l in layers(page):
		l['decal_Y_Ratio'] -= shift
	if span:
		set_speeds(page, [layers(page)[0]['parallaxScalingSpeedX'] * span ** i for i in range(len(layers(page)))])
	colours = page_of('', [], sky=sky, ground=ground)
	for key in ('topHalf_top', 'topHalf_bottom', 'bottomHalf_top', 'bottomHalf_bottom'):
		page[key] = colours[key]
	return page


def winter():
	"""Simon's Hiver (round 1: 1), moved down to fill the screen under a winter sky."""
	return lowered('demo/assets/hiver/Hiver.plaxpj', 45, ('8e8aa6', 'dcdbe6'), ('e8eef0', 'e8eef0')), 'demo/assets'


def spring():
	"""Simon's Printemps, moved down, its speeds stretched to a x1.35 ratio (its span was 2x, under the skill's 3x)."""
	return lowered('demo/assets/printemps/Printemps.plaxpj', 52, ('f2b89c', 'eecab7'), ('efcab8', 'efcab8'), 1.35), \
		'demo/assets'


def city():
	"""City.atlas, pixel art (only a .plax exists for it): sky, far buildings, buildings, street."""
	back = 0.012
	page = page_of('City.atlas', [
		layer('skill-desc_0003_bg', speed=back, size=1.0, dy=0),
		layer('skill-desc_0002_far-buildings', speed=back * 1.5, size=0.8, dx=20, dy=10),
		layer('skill-desc_0001_buildings', speed=back * 1.5 ** 2, size=1.0, dx=40, dy=0),
		layer('skill-desc_0000_foreground', speed=back * 1.5 ** 3, size=1.0, dx=10, dy=-2),
	], sky=('10261f', '2f7a57'), ground=('0d1512', '0d1512'))
	return page, 'editor/Files/Demos'


PAGES = [
	('calm-day', calm_day, 'Calm: day over mountains and forest (Simon\'s page)'),
	('calm-sunset', calm_sunset, 'Calm at sunset: the same layout on the silhouette atlas'),
	('meadow', meadow, 'Meadow: Calm\'s far scene with bushes, grass and a road in front'),
	('one-night', one_night, 'One night: pines, mountains and moving water (Simon\'s page)'),
	('purple-fairy', purple_fairy, 'Purple fairy: a glowing forest (Simon\'s page)'),
	('forest', forest, 'Forest: painted trees and mist (Simon\'s calmTree)'),
	('winter', winter, 'Winter: Hiver filling the screen under a winter sky'),
	('spring', spring, 'Spring: Printemps filling the screen, deeper speed span'),
	('city', city, 'City: pixel-art buildings at night'),
]


def main(argv):
	out = argv[1] if len(argv) > 1 else 'demo/showcase'
	os.makedirs(os.path.join(ROOT, out), exist_ok=True)
	scenes = []
	for name, build, about in PAGES:
		page, atlas_dir = build()
		path = os.path.join(out, name + '.jplax')
		with open(os.path.join(ROOT, path), 'w', encoding='utf-8') as f:
			json.dump(page, f, indent=1)
			f.write('\n')
		scenes.append({'id': name, 'page': path, 'atlasDir': atlas_dir, 'name': name, 'about': about,
		               'lint': lint(page)})
	with open(os.path.join(ROOT, out, 'round.json'), 'w', encoding='utf-8') as f:
		json.dump({'note': 'Showcase bank (r108): the pages the store listings and videos show.', 'scenes': scenes},
		          f, indent=1)
		f.write('\n')
	print('%d pages in %s' % (len(scenes), out))


if __name__ == '__main__':
	sys.exit(main(sys.argv))
