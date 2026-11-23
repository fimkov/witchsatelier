package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading.ReadSign;
import com.fimkov.witchsatelier.glyph.recognize.SymbolRecognizer.Match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class GlyphAnalyzer {
    static final double SIDE_SIGIL_SCORE = 0.5, SIDE_SIGIL_MARGIN = 0.12, FRAME_GIVE = 0.08, FRAME_SURE = 0.5;

    public static final double MIN_SCORE = 0.35;

    static final double SURE_SIGN = 0.5, PICTURE_SCORE = 0.55;

    static final java.util.Set<SignType> PICTURES = java.util.EnumSet.of(SignType.DANCING_PUPPET, SignType.HORSE);

    static final double INNER_RING_MIN = 0.5;

    private GlyphAnalyzer() {}

    public static GlyphReading analyze(List<List<Pt>> strokes, double inkWidth) {
        return analyze(strokes, inkWidth, Math.PI / 2);
    }

    public static GlyphReading analyzeFromAnySide(List<List<Pt>> strokes, double inkWidth, double upAngle) {
        List<List<Pt>> thin = PointCloud.thin(strokes, inkWidth);
        return SymbolRecognizer.cached(() -> fromAnySide(thin, inkWidth, upAngle));
    }

    private static GlyphReading fromAnySide(List<List<Pt>> strokes, double inkWidth, double upAngle) {
        GlyphReading best = analyze(strokes, inkWidth, upAngle);
        if (best.ring() == null) return best;
        double bestScore = sideScore(best) + 0.05;

        for (int k = 1; k < 8; k++) {
            GlyphReading r = analyze(strokes, inkWidth, upAngle + k * Math.PI / 4);
            if (sideScore(r) > bestScore) {
                best = r;
                bestScore = sideScore(r);
            }
        }
        return best;
    }

    private static double sideScore(GlyphReading r) {
        return r.centerScore() + (r.core() != null ? sideScore(r.core()) : 0);
    }

    static List<Match> pupilMakesAnEye(List<List<Pt>> strokes, List<Integer> g, List<Match> rank, double outwardAngle) {
        if (rank.isEmpty() || rank.get(0).template() == null || rank.get(0).template().sign() != SignType.DIAMOND) return rank;
        if (!hasPupil(strokes, g)) return rank;
        Match eye = SymbolRecognizer.exactSign(pick(strokes, g), outwardAngle, SignType.EYE);
        if (eye.template() == null || eye.score() < 0.25) return rank;
        List<Match> out = new ArrayList<>(rank);
        out.removeIf(m -> m.template() != null && m.template().sign() == SignType.EYE);
        out.add(0, eye);
        return out;
    }

    static List<Match> barOrHook(List<List<Pt>> strokes, List<Integer> g, List<Match> rank) {
        if (rank.size() < 2) return rank;
        Match top = rank.get(0);
        if (top.template() == null) return rank;
        SignType t = top.template().sign();
        if (t != SignType.BEND && t != SignType.COLUMN) return rank;
        SignType other = t == SignType.BEND ? SignType.COLUMN : SignType.BEND;
        int at = -1;
        for (int i = 1; i < rank.size(); i++) if (rank.get(i).template().sign() == other) { at = i; break; }
        if (at < 0 || top.score() - rank.get(at).score() > 0.12 || rank.get(at).score() < 0.3) return rank;
        int sides = endSides(strokes, g);
        if (sides == 0 || (sides == 2) == (t == SignType.COLUMN)) return rank;
        List<Match> out = new ArrayList<>(rank);
        Match m = out.remove(at);
        out.add(0, m);
        return out;
    }

    static int endSides(List<List<Pt>> strokes, List<Integer> g) {
        List<Pt> pts = new ArrayList<>();
        for (int i : g) pts.addAll(strokes.get(i));
        if (pts.size() < 8) return 0;
        Pt c = PointCloud.centroid(pts);

        double sxx = 0, syy = 0, sxy = 0;
        for (Pt p : pts) { double dx = p.x() - c.x(), dy = p.y() - c.y(); sxx += dx * dx; syy += dy * dy; sxy += dx * dy; }
        double ang = 0.5 * Math.atan2(2 * sxy, sxx - syy);
        double ax = Math.cos(ang), ay = Math.sin(ang);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (Pt p : pts) { double a = (p.x() - c.x()) * ax + (p.y() - c.y()) * ay; lo = Math.min(lo, a); hi = Math.max(hi, a); }
        double len = hi - lo;
        if (len <= 0) return 0;

        List<Double> mid = new ArrayList<>();
        for (Pt p : pts) {
            double a = (p.x() - c.x()) * ax + (p.y() - c.y()) * ay;
            if (a > lo + 0.3 * len && a < hi - 0.3 * len) mid.add(-(p.x() - c.x()) * ay + (p.y() - c.y()) * ax);
        }
        if (mid.isEmpty()) return 0;
        Collections.sort(mid);
        double line = mid.get(mid.size() / 2);
        int best = 0;
        double bestReach = 0;
        for (int end = 0; end < 2; end++) {
            double pos = 0, neg = 0;
            for (Pt p : pts) {
                double a = (p.x() - c.x()) * ax + (p.y() - c.y()) * ay;
                boolean near = end == 0 ? a < lo + 0.25 * len : a > hi - 0.25 * len;
                if (!near) continue;
                double d = -(p.x() - c.x()) * ay + (p.y() - c.y()) * ax - line;
                pos = Math.max(pos, d);
                neg = Math.max(neg, -d);
            }
            double reach = Math.max(pos, neg);
            if (reach < 0.12 * len || reach <= bestReach) continue;
            bestReach = reach;
            best = Math.min(pos, neg) > 0.4 * reach ? 2 : Math.min(pos, neg) < 0.2 * reach ? 1 : 0;
        }
        return best;
    }

    static boolean hasPupil(List<List<Pt>> strokes, List<Integer> g) {
        if (g.size() < 2) return false;
        int body = g.get(0);
        for (int i : g) if (PointCloud.pathLength(strokes.get(i)) > PointCloud.pathLength(strokes.get(body))) body = i;
        double[] bb = bbox(strokes.get(body));
        double size = Math.max(bb[2] - bb[0], bb[3] - bb[1]);
        Pt mid = new Pt((bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2);
        for (int i : g) {
            if (i == body) continue;
            double[] b = bbox(strokes.get(i));
            if (Math.max(b[2] - b[0], b[3] - b[1]) < 0.3 * size && PointCloud.centroid(strokes.get(i)).dist(mid) < 0.25 * size) return true;
        }
        return false;
    }

    private static double[] bbox(List<Pt> s) {
        double[] b = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Pt p : s) {
            b[0] = Math.min(b[0], p.x()); b[1] = Math.min(b[1], p.y());
            b[2] = Math.max(b[2], p.x()); b[3] = Math.max(b[3], p.y());
        }
        return b;
    }

    private static Match center(List<List<Pt>> sym, double turn) {
        if (Math.abs(turn) < 1e-6) return SymbolRecognizer.classifyCenter(sym);
        List<List<Pt>> rot = new ArrayList<>();
        for (List<Pt> s : sym) {
            List<Pt> q = new ArrayList<>(s.size());
            for (Pt p : s) q.add(p.rotate(turn));
            rot.add(q);
        }
        return SymbolRecognizer.classifyCenter(rot);
    }

    public static GlyphReading analyze(List<List<Pt>> strokes, double inkWidth, double upAngle) {
        List<List<Pt>> thin = PointCloud.thin(strokes, inkWidth);
        return SymbolRecognizer.cached(() -> analyzeNow(thin, inkWidth, upAngle));
    }

    private static GlyphReading analyzeNow(List<List<Pt>> strokes, double inkWidth, double upAngle) {
        double turn = Math.PI / 2 - upAngle;
        List<RingFinder.Ring> rings = RingFinder.find(strokes, inkWidth);
        if (rings.isEmpty()) {
            return new GlyphReading(null, List.of(), null, null, 0, List.of(), 0, new Pt(0, 0), 0, List.of());
        }
        RingFinder.Ring outer = rings.get(0);
        Pt c = outer.center();
        double r = outer.radius();

        List<RingFinder.Ring> inner = new ArrayList<>();
        List<Integer> used = new ArrayList<>(outer.strokes());
        boolean[] isRing = new boolean[strokes.size()];
        for (RingFinder.Ring ring : rings) {
            if (ring == outer) {
                for (int i : ring.strokes()) isRing[i] = true;
            } else if (ring.center().dist(c) + ring.radius() <= r * 1.05 && ring.radius() >= INNER_RING_MIN * r) {
                for (int i : ring.strokes()) isRing[i] = true;
                inner.add(ring);
                used.addAll(ring.strokes());
            }
        }

        RingFinder.Ring nest = null;
        for (RingFinder.Ring ring : inner) {
            if (ring.center().dist(c) < 0.25 * r && (nest == null || ring.radius() > nest.radius())) nest = ring;
        }
        GlyphReading core = null;
        boolean[] inCore = new boolean[strokes.size()];
        if (nest != null) {
            List<Integer> coreIdx = new ArrayList<>();
            for (int i = 0; i < strokes.size(); i++) {
                if (strokes.get(i).isEmpty() || outer.strokes().contains(i)) continue;
                if (nest.strokes().contains(i) || PointCloud.centroid(strokes.get(i)).dist(nest.center()) < nest.radius() * 1.02) coreIdx.add(i);
            }
            List<List<Pt>> sub = new ArrayList<>();
            for (int i : coreIdx) sub.add(strokes.get(i));
            GlyphReading inside = analyze(sub, inkWidth, upAngle);
            if (inside.ring() != null && inside.ring().radius() > nest.radius() * 0.9) {
                List<Integer> mapped = new ArrayList<>();
                for (int i : inside.usedStrokes()) mapped.add(coreIdx.get(i));
                core = new GlyphReading(inside.ring(), inside.innerRings(), inside.sigil(), inside.centerSign(), inside.centerScore(),
                        inside.signs(), inside.unknown(), inside.imbalance(), inside.neatness(), List.copyOf(mapped), inside.core(), inside.sides());
                for (int i : coreIdx) inCore[i] = true;
                used.addAll(coreIdx);
            }
        }

        List<Integer> inside = new ArrayList<>();
        for (int i = 0; i < strokes.size(); i++) {
            if (isRing[i] || inCore[i] || strokes.get(i).isEmpty()) continue;
            if (PointCloud.centroid(strokes.get(i)).dist(c) < r) inside.add(i);
        }
        used.addAll(inside);

        List<List<Pt>> ringInk = new ArrayList<>();
        for (int i : outer.strokes()) ringInk.add(strokes.get(i));
        double over = inkWidth * 1.5;
        inside.removeIf(i -> {
            List<Pt> st = strokes.get(i);
            int on = 0;
            for (Pt p : st) if (nearInk(p, ringInk, over)) on++;
            return on >= st.size() * 0.8;
        });
        List<List<Integer>> seals = stripFrames(strokes, inside, c, r, inkWidth, turn);
        List<Integer> sealed = new ArrayList<>();
        for (List<Integer> seal : seals) sealed.addAll(seal);
        List<List<Integer>> groups = group(strokes, inside, Math.max(inkWidth * 2.5, 0.07 * r));
        joinSeals(groups, seals);
        mergeEnclosed(strokes, groups, c, r);
        mergeParts(strokes, groups, c, r);
        for (var it = groups.iterator(); it.hasNext(); ) {
            List<Integer> g = it.next();
            if (centroid(strokes, g).dist(c) < 0.35 * r || !struckOut(strokes, g, c, turn)) continue;
            it.remove();
            used.removeAll(g);
        }

        List<Integer> centerGroup = null;
        List<List<Integer>> centerParts = new ArrayList<>();
        List<List<Integer>> outerGroups = new ArrayList<>();
        for (List<Integer> g : groups) {
            if (centroid(strokes, g).dist(c) < 0.35 * r) {
                List<Integer> far = new ArrayList<>();
                for (int i : g) if (PointCloud.centroid(strokes.get(i)).dist(c) > 0.38 * r) far.add(i);
                if (!far.isEmpty() && far.size() < g.size()) {
                    List<Integer> near = new ArrayList<>(g);
                    near.removeAll(far);
                    Pt fc = centroid(strokes, far);
                    Match asSign = SymbolRecognizer.classifySign(pick(strokes, far), Math.atan2(fc.y() - c.y(), fc.x() - c.x()));
                    Match withIt = center(pick(strokes, g), turn);
                    Match without = center(pick(strokes, near), turn);
                    if (asSign.template() != null && asSign.score() >= MIN_SCORE && without.score() >= withIt.score() - 0.05) {
                        outerGroups.add(far);
                        g = near;
                    }
                }
                if (centerGroup == null) centerGroup = new ArrayList<>();
                centerGroup.addAll(g);
                centerParts.add(g);
            } else {
                outerGroups.add(g);
            }
        }

        if (centerGroup != null) {
            List<List<Integer>> specks = new ArrayList<>();
            double sigilSize = size(strokes, centerGroup);
            for (List<Integer> g : outerGroups) {
                if (centroid(strokes, g).dist(c) <= 0.62 * r && size(strokes, g) < 0.3 * sigilSize) specks.add(g);
            }
            if (!specks.isEmpty()) {
                List<Integer> with = new ArrayList<>(centerGroup);
                for (List<Integer> g : specks) with.addAll(g);
                boolean joins = center(pick(strokes, with), turn).score() >= center(pick(strokes, centerGroup), turn).score() - 0.03;
                for (List<Integer> g : specks) {
                    if (joins || signScore(strokes, g, c) < SURE_SIGN) outerGroups.remove(g);
                }
                if (joins) {
                    centerGroup = with;
                    centerParts.clear();
                    centerParts.add(with);
                }
            }
        }

        if (centerGroup != null) {
            List<List<Integer>> stray = new ArrayList<>();
            for (List<Integer> g : outerGroups) {
                Pt gc = centroid(strokes, g);
                if (gc.dist(c) > 0.62 * r) continue;
                Match asSign = SymbolRecognizer.classifySign(pick(strokes, g), Math.atan2(gc.y() - c.y(), gc.x() - c.x()));
                if (asSign.template() == null || asSign.score() < MIN_SCORE) stray.add(g);
            }
            if (!stray.isEmpty()) {
                List<Integer> bigger = new ArrayList<>(centerGroup);
                for (List<Integer> g : stray) bigger.addAll(g);
                Match before = center(pick(strokes, centerGroup), turn);
                Match after = center(pick(strokes, bigger), turn);
                if (after.template() != null && after.score() >= MIN_SCORE && after.score() > before.score()) {
                    centerGroup = bigger;
                    outerGroups.removeAll(stray);
                    centerParts.clear();
                    centerParts.add(bigger);
                }
            }
        }

        ReadSign enclosing = null;
        if (centerParts.size() > 1) {
            centerParts.sort(Comparator.comparingDouble(g -> centroid(strokes, g).dist(c)));
            Match whole = center(pick(strokes, centerGroup), turn);
            Match innerPart = center(pick(strokes, centerParts.get(0)), turn);
            List<Integer> rest = new ArrayList<>();
            for (int i = 1; i < centerParts.size(); i++) rest.addAll(centerParts.get(i));
            Match around = center(pick(strokes, rest), turn);
            boolean split = innerPart.template() != null && innerPart.template().sigil() != null && innerPart.score() >= MIN_SCORE
                    && around.template() != null && around.template().sign() != null && around.score() >= MIN_SCORE
                    && (innerPart.score() + around.score()) / 2 > whole.score();
            if (split) {
                centerGroup = centerParts.get(0);
                enclosing = new ReadSign(around.template().sign(), around.template().reversed(), around.orientation(), Math.PI / 2,
                        bboxDiagonal(pick(strokes, rest)), around.score(), centroid(strokes, rest), List.copyOf(rest));
            }
        }

        double scoreSum = 0;
        int scored = 0, unknown = 0;
        Sigil sigil = null;
        SignType centerSign = null;
        double centerScore = 0;
        double speck = inkWidth * 1.5;
        if (centerGroup != null) {
            Match m = bboxDiagonal(pick(strokes, centerGroup)) < speck ? Match.NONE : center(pick(strokes, centerGroup), turn);
            if (m.template() != null && m.score() >= MIN_SCORE) {
                sigil = m.template().sigil();
                centerSign = m.template().sign();
                centerScore = m.score();
                scoreSum += m.score();
                scored++;
            } else {
                unknown++;
            }
        }

        List<ReadSign> signs = new ArrayList<>();
        double wx = 0, wy = 0, wsum = 0;

        List<List<Match>> ranked = new ArrayList<>();
        Match[] chosen = new Match[outerGroups.size()];
        Map<SignType, Integer> votes = new EnumMap<>(SignType.class);
        for (int k = 0; k < outerGroups.size(); k++) {
            List<Integer> g = outerGroups.get(k);
            Pt cen = centroid(strokes, g);
            if (bboxDiagonal(pick(strokes, g)) < speck) {
                ranked.add(List.of());
                continue;
            }
            List<Match> rank = SymbolRecognizer.rankSign(pick(strokes, g), Math.atan2(cen.y() - c.y(), cen.x() - c.x()));
            rank = pupilMakesAnEye(strokes, g, rank, Math.atan2(cen.y() - c.y(), cen.x() - c.x()));
            rank = barOrHook(strokes, g, rank);
            ranked.add(rank);
            Match best = rank.isEmpty() ? Match.NONE : rank.get(0);
            double margin = rank.size() > 1 ? best.score() - rank.get(1).score() : best.score();

            boolean picture = best.template() != null && PICTURES.contains(best.template().sign());
            if (best.template() != null && (picture ? best.score() >= PICTURE_SCORE : best.score() >= MIN_SCORE || (best.score() >= 0.27 && margin >= 0.1))) {
                chosen[k] = best;
                votes.merge(best.template().sign(), 1, Integer::sum);
            }
        }

        SignType common = null;
        int most = 1;
        for (var e : votes.entrySet()) if (e.getValue() > most) { most = e.getValue(); common = e.getKey(); }
        if (common != null) {
            for (int k = 0; k < outerGroups.size(); k++) {
                if (chosen[k] != null && chosen[k].template().sign() == common) continue;
                List<Match> rank = ranked.get(k);
                if (rank.isEmpty()) continue;

                double give = chosen[k] != null && chosen[k].score() >= 0.5 ? 0.08 : 0.06;
                for (Match m : rank) {
                    if (m.template().sign() == common && m.score() >= 0.24 && rank.get(0).score() - m.score() <= give) { chosen[k] = m; break; }
                }
            }
        }

        if (core != null && sigil == null) {
            Match bestSigil = null;
            int at = -1;
            for (int k = 0; k < outerGroups.size(); k++) {
                if (bboxDiagonal(pick(strokes, outerGroups.get(k))) < speck) continue;
                Match cm = center(pick(strokes, outerGroups.get(k)), turn);
                if (cm.template() == null || cm.template().sigil() == null || cm.score() < MIN_SCORE) continue;
                double asSign = chosen[k] == null ? 0 : chosen[k].score();
                if (cm.score() < asSign + 0.05) continue;
                if (bestSigil == null || cm.score() > bestSigil.score()) { bestSigil = cm; at = k; }
            }
            if (bestSigil != null) {
                sigil = bestSigil.template().sigil();
                centerScore = bestSigil.score();
                scoreSum += bestSigil.score();
                scored++;
                chosen[at] = null;
                outerGroups.set(at, List.of());
            }
        }

        List<Sigil> sides = new ArrayList<>();
        if (core == null && (sigil != null || centerSign != null)) {
            for (int k = 0; k < outerGroups.size(); k++) {
                if (outerGroups.get(k).isEmpty() || bboxDiagonal(pick(strokes, outerGroups.get(k))) < speck) continue;
                Match cm = center(pick(strokes, outerGroups.get(k)), turn);
                boolean inSeal = sealed.containsAll(outerGroups.get(k));
                if (cm.template() == null || cm.template().sigil() == null || cm.template().sigil() == Sigil.APPLE
                        || cm.score() < (inSeal ? MIN_SCORE : SIDE_SIGIL_SCORE)) continue;
                double asSign = chosen[k] == null ? 0 : chosen[k].score();
                if (!inSeal && cm.score() < asSign + SIDE_SIGIL_MARGIN) continue;
                sides.add(cm.template().sigil());
                scoreSum += cm.score();
                scored++;
                chosen[k] = null;
                outerGroups.set(k, List.of());
            }
        }
        for (int k = 0; k < outerGroups.size(); k++) {
            List<Integer> g = outerGroups.get(k);
            if (g.isEmpty()) continue;
            Match m = chosen[k];
            if (m == null) {
                unknown++;
                continue;
            }
            List<List<Pt>> sym = pick(strokes, g);
            Pt cen = centroid(strokes, g);
            double angle = Math.atan2(cen.y() - c.y(), cen.x() - c.x());
            double size = bboxDiagonal(sym);
            double length = 0;
            for (List<Pt> s : sym) length += PointCloud.pathLength(s);
            signs.add(new ReadSign(m.template().sign(), m.template().reversed(), m.orientation(), angle, size,
                    m.score(), cen, List.copyOf(g)));
            wx += Math.cos(angle) * length;
            wy += Math.sin(angle) * length;
            wsum += length;
            scoreSum += m.score();
            scored++;
        }
        if (enclosing != null) {
            signs.add(enclosing);
            scoreSum += enclosing.score();
            scored++;
        }
        Pt imbalance = wsum > 0 ? new Pt(wx / wsum, wy / wsum) : new Pt(0, 0);
        double neatness = scored > 0 ? scoreSum / (scored + unknown) : 0;
        return new GlyphReading(outer, inner, sigil, centerSign, centerScore, signs, unknown, imbalance, neatness,
                List.copyOf(new java.util.LinkedHashSet<>(used)), core, List.copyOf(sides));
    }

    static void joinSeals(List<List<Integer>> groups, List<List<Integer>> seals) {
        for (List<Integer> seal : seals) {
            List<Integer> joined = null;
            for (int k = 0; k < groups.size(); k++) {
                if (!seal.containsAll(groups.get(k))) continue;
                if (joined == null) {
                    joined = groups.get(k);
                } else {
                    joined.addAll(groups.get(k));
                    groups.remove(k--);
                }
            }
        }
    }

    static List<List<Integer>> stripFrames(List<List<Pt>> strokes, List<Integer> inside, Pt c, double r, double inkWidth, double turn) {
        List<List<Integer>> sealed = new ArrayList<>();
        double tol = Math.max(0.08 * r, inkWidth * 2.5);
        List<double[]> lunes = new ArrayList<>();
        inside.removeIf(i -> {
            List<Pt> st = strokes.get(i);
            if (!isLuneArc(st, c, r, tol)) return false;
            Pt a = st.get(0), b = st.get(st.size() - 1);
            double ang = Math.atan2((a.y() + b.y()) / 2 - c.y(), (a.x() + b.x()) / 2 - c.x());
            Pt rim = new Pt(c.x() + Math.cos(ang) * r, c.y() + Math.sin(ang) * r);
            double rho = 0;
            for (Pt q : st) rho += q.dist(rim);
            lunes.add(new double[]{rim.x(), rim.y(), rho / st.size()});
            return true;
        });
        for (int i : new ArrayList<>(inside)) {
            double[] fc = circleOf(strokes.get(i));
            if (fc == null || fc[2] > 0.45 * r || fc[2] < 0.06 * r) continue;
            Pt at = new Pt(fc[0], fc[1]);
            boolean inLune = false;
            for (double[] l : lunes) if (at.dist(new Pt(l[0], l[1])) < l[2] * 0.95 && fc[2] < l[2]) inLune = true;
            if (!lunes.isEmpty() && at.dist(c) < 0.15 * r && fc[2] < 0.45 * r) inLune = true;
            List<Integer> content = new ArrayList<>();
            for (int j : inside) {
                if (j == i) continue;
                int in = 0;
                for (Pt p : strokes.get(j)) if (p.dist(at) <= fc[2] * 1.25) in++;
                if (PointCloud.centroid(strokes.get(j)).dist(at) <= fc[2] && in >= strokes.get(j).size() * 0.6) content.add(j);
            }
            if (content.isEmpty()) continue;
            List<Integer> with = new ArrayList<>(content);
            with.add(i);
            Match framed = asSymbol(strokes, with, at, c, r, turn), bare = asSymbol(strokes, content, at, c, r, turn);
            if (inLune || bare.template() != null && bare.score() >= MIN_SCORE && (bare.score() >= framed.score() - FRAME_GIVE || bare.score() >= FRAME_SURE)) {
                inside.remove(Integer.valueOf(i));
                if (at.dist(c) >= 0.2 * r) sealed.add(content);
            }
        }
        return sealed;
    }

    static Match asSymbol(List<List<Pt>> strokes, List<Integer> g, Pt at, Pt c, double r, double turn) {
        Match centre = Match.NONE;
        for (int k = 0; k < 8; k++) {
            Match m = center(pick(strokes, g), turn + k * Math.PI / 4);
            if (m.template() != null && (centre.template() == null || m.score() > centre.score())) centre = m;
        }
        if (at.dist(c) < 0.2 * r) return centre;
        Match sign = SymbolRecognizer.classifySign(pick(strokes, g), Math.atan2(at.y() - c.y(), at.x() - c.x()));
        return sign.template() != null && sign.score() > centre.score() ? sign : centre;
    }

    static boolean isLuneArc(List<Pt> st, Pt c, double r, double tol) {
        if (st.size() < 4) return false;
        Pt a = st.get(0), b = st.get(st.size() - 1);
        if (Math.abs(a.dist(c) - r) > tol || Math.abs(b.dist(c) - r) > tol || a.dist(b) < 0.25 * r) return false;
        double nearest = Double.MAX_VALUE;
        for (Pt p : st) nearest = Math.min(nearest, p.dist(c));
        return nearest < 0.85 * r && nearest > 0.3 * r && PointCloud.pathLength(st) > 0.3 * r;
    }

    static double[] circleOf(List<Pt> st) {
        if (st.size() < 12) return null;
        Pt m = PointCloud.centroid(st);
        double sum = 0;
        for (Pt p : st) sum += p.dist(m);
        double mean = sum / st.size(), var = 0, worst = 0;
        for (Pt p : st) {
            var += (p.dist(m) - mean) * (p.dist(m) - mean);
            worst = Math.max(worst, Math.abs(p.dist(m) - mean));
        }
        if (mean <= 0 || Math.sqrt(var / st.size()) / mean > 0.09 || worst / mean > 0.2) return null;
        double[] ang = new double[st.size()];
        for (int k = 0; k < st.size(); k++) ang[k] = Math.atan2(st.get(k).y() - m.y(), st.get(k).x() - m.x());
        Arrays.sort(ang);
        double gap = ang[0] + Math.PI * 2 - ang[ang.length - 1];
        for (int k = 1; k < ang.length; k++) gap = Math.max(gap, ang[k] - ang[k - 1]);
        return gap > Math.toRadians(60) ? null : new double[]{m.x(), m.y(), mean};
    }

    static void mergeEnclosed(List<List<Pt>> strokes, List<List<Integer>> groups, Pt c, double r) {
        for (int i = 0; i < groups.size(); i++) {
            List<Integer> big = groups.get(i);
            if (centroid(strokes, big).dist(c) < 0.35 * r) continue;
            List<Pt> pts = new ArrayList<>();
            for (int k : big) pts.addAll(strokes.get(k));
            double[] bb = bbox(pts);
            double size = Math.max(bb[2] - bb[0], bb[3] - bb[1]);
            Pt mid = new Pt((bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2);
            List<Pt> hull = hull(pts);
            for (int j = 0; j < groups.size(); j++) {
                if (j == i) continue;
                List<Integer> mark = groups.get(j);
                List<Pt> mp = new ArrayList<>();
                for (int k : mark) mp.addAll(strokes.get(k));
                double[] mb = bbox(mp);
                Pt mc = PointCloud.centroid(mp);
                if (Math.max(mb[2] - mb[0], mb[3] - mb[1]) > 0.35 * size || mc.dist(mid) > 0.3 * size || !inside(hull, mc)) continue;
                big.addAll(mark);
                groups.remove(j);
                if (j < i) i--;
                j--;
            }
        }
    }

    static void mergeParts(List<List<Pt>> strokes, List<List<Integer>> groups, Pt c, double r) {
        boolean[] seen = new boolean[groups.size()];
        List<List<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            if (seen[i] || centroid(strokes, groups.get(i)).dist(c) < 0.35 * r) continue;
            List<Integer> comp = new ArrayList<>(List.of(i));
            seen[i] = true;
            for (int q = 0; q < comp.size(); q++) {
                for (int j = 0; j < groups.size(); j++) {
                    if (seen[j] || centroid(strokes, groups.get(j)).dist(c) < 0.35 * r) continue;
                    if (groupDist(strokes, groups.get(comp.get(q)), groups.get(j)) <= 0.2 * r) { seen[j] = true; comp.add(j); }
                }
            }
            if (comp.size() > 2) clusters.add(comp);
        }
        List<List<Integer>> absorbed = new ArrayList<>();
        for (List<Integer> comp : clusters) {
            List<Integer> all = new ArrayList<>();
            double mean = 0;
            for (int i : comp) { all.addAll(groups.get(i)); mean += signScore(strokes, groups.get(i), c) / comp.size(); }
            double together = signScore(strokes, all, c);
            if (size(strokes, all) > 0.5 * r || together < Math.max(MIN_SCORE, mean)) continue;
            List<Integer> first = groups.get(comp.get(0));
            for (int k = 1; k < comp.size(); k++) { first.addAll(groups.get(comp.get(k))); absorbed.add(groups.get(comp.get(k))); }
        }
        for (List<Integer> g : absorbed) groups.removeIf(x -> x == g);
        boolean merged = true;
        while (merged) {
            merged = false;
            double bestGain = 0.05;
            int bi = -1, bj = -1;
            for (int i = 0; i < groups.size(); i++) {
                List<Integer> a = groups.get(i);
                if (centroid(strokes, a).dist(c) < 0.35 * r) continue;
                for (int j = i + 1; j < groups.size(); j++) {
                    List<Integer> b = groups.get(j);
                    if (centroid(strokes, b).dist(c) < 0.35 * r) continue;
                    if (groupDist(strokes, a, b) > 0.2 * r) continue;
                    List<Integer> ab = new ArrayList<>(a);
                    ab.addAll(b);
                    if (size(strokes, ab) > 0.5 * r) continue;
                    double gain = signScore(strokes, ab, c) - Math.max(signScore(strokes, a, c), signScore(strokes, b, c));
                    if (gain > bestGain) { bestGain = gain; bi = i; bj = j; }
                }
            }
            if (bi >= 0) {
                groups.get(bi).addAll(groups.get(bj));
                groups.remove(bj);
                merged = true;
            }
        }
    }

    private static double signScore(List<List<Pt>> strokes, List<Integer> g, Pt c) {
        Pt gc = centroid(strokes, g);
        Match m = SymbolRecognizer.classifySign(pick(strokes, g), Math.atan2(gc.y() - c.y(), gc.x() - c.x()));
        return m.template() == null ? 0 : m.score();
    }

    private static double groupDist(List<List<Pt>> strokes, List<Integer> a, List<Integer> b) {
        double d = Double.POSITIVE_INFINITY;
        for (int i : a) for (int j : b) d = Math.min(d, minDist(strokes.get(i), strokes.get(j)));
        return d;
    }

    private static boolean nearInk(Pt p, List<List<Pt>> ink, double tol) {
        double t2 = tol * tol;
        for (List<Pt> s : ink) {
            for (int i = 1; i < s.size(); i++) {
                Pt a = s.get(i - 1), b = s.get(i);
                double dx = b.x() - a.x(), dy = b.y() - a.y(), l2 = dx * dx + dy * dy;
                double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / l2));
                double ex = a.x() + dx * t - p.x(), ey = a.y() + dy * t - p.y();
                if (ex * ex + ey * ey <= t2) return true;
            }
        }
        return false;
    }

    static double size(List<List<Pt>> strokes, List<Integer> g) {
        List<Pt> pts = new ArrayList<>();
        for (int k : g) pts.addAll(strokes.get(k));
        double[] b = bbox(pts);
        return Math.max(b[2] - b[0], b[3] - b[1]);
    }

    private static List<Pt> hull(List<Pt> pts) {
        List<Pt> p = new ArrayList<>(pts);
        p.sort((a, b) -> a.x() != b.x() ? Double.compare(a.x(), b.x()) : Double.compare(a.y(), b.y()));
        if (p.size() < 3) return p;
        List<Pt> h = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int start = h.size();
            for (Pt q : p) {
                while (h.size() >= start + 2 && cross(h.get(h.size() - 2), h.get(h.size() - 1), q) <= 0) h.remove(h.size() - 1);
                h.add(q);
            }
            h.remove(h.size() - 1);
            Collections.reverse(p);
        }
        return h;
    }

    private static double cross(Pt o, Pt a, Pt b) {
        return (a.x() - o.x()) * (b.y() - o.y()) - (a.y() - o.y()) * (b.x() - o.x());
    }

    private static boolean inside(List<Pt> poly, Pt p) {
        boolean in = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            Pt a = poly.get(i), b = poly.get(j);
            if ((a.y() > p.y()) != (b.y() > p.y()) && p.x() < (b.x() - a.x()) * (p.y() - a.y()) / (b.y() - a.y()) + a.x()) in = !in;
        }
        return in;
    }

    static List<List<Integer>> group(List<List<Pt>> strokes, List<Integer> idx, double mergeDist) {
        int n = idx.size();
        int[] parent = new int[n];
        double[][] box = new double[n][];
        for (int i = 0; i < n; i++) { parent[i] = i; box[i] = bbox(strokes.get(idx.get(i))); }
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                double gx = Math.max(box[a][0] - box[b][2], box[b][0] - box[a][2]), gy = Math.max(box[a][1] - box[b][3], box[b][1] - box[a][3]);
                if (gx >= mergeDist || gy >= mergeDist || find(parent, a) == find(parent, b)) continue;
                if (closerThan(strokes.get(idx.get(a)), strokes.get(idx.get(b)), mergeDist)) {
                    parent[find(parent, a)] = find(parent, b);
                }
            }
        }
        List<List<Integer>> out = new ArrayList<>();
        int[] slot = new int[n];
        Arrays.fill(slot, -1);
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            if (slot[root] < 0) { slot[root] = out.size(); out.add(new ArrayList<>()); }
            out.get(slot[root]).add(idx.get(i));
        }
        return out;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; }
        return i;
    }

    static boolean struckOut(List<List<Pt>> strokes, List<Integer> g, Pt c, double turn) {
        if (g.size() < 2) return false;
        int longest = g.get(0);
        for (int i : g) if (PointCloud.pathLength(strokes.get(i)) > PointCloud.pathLength(strokes.get(longest))) longest = i;
        for (int i : List.of(longest)) {
            List<Pt> st = strokes.get(i);
            if (st.size() < 2) continue;
            Pt a = st.get(0), b = st.get(st.size() - 1);
            double path = PointCloud.pathLength(st), chord = a.dist(b);
            if (path <= 0 || chord < 0.95 * path) continue;
            List<Integer> rest = new ArrayList<>(g);
            rest.remove((Integer) i);
            List<List<Pt>> sym = pick(strokes, rest);
            double size = bboxDiagonal(sym);
            if (size <= 0) continue;
            Pt rc = centroid(strokes, rest);
            if (segmentDist(rc, a, b) > 0.25 * size) continue;
            double dx = (b.x() - a.x()) / chord, dy = (b.y() - a.y()) / chord;
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, sxx = 0, syy = 0, sxy = 0;
            int pts = 0;
            for (List<Pt> q : sym) for (Pt p : q) {
                double along = (p.x() - a.x()) * dx + (p.y() - a.y()) * dy;
                lo = Math.min(lo, along);
                hi = Math.max(hi, along);
                double ex = p.x() - rc.x(), ey = p.y() - rc.y();
                sxx += ex * ex; syy += ey * ey; sxy += ex * ey;
                pts++;
            }
            if (pts == 0 || lo < 0.12 * chord || hi > 0.88 * chord) continue;
            double tr = sxx + syy, det = sxx * syy - sxy * sxy, disc = Math.sqrt(Math.max(0, tr * tr / 4 - det));
            double big = tr / 2 + disc, small = Math.max(1e-12, tr / 2 - disc);
            if (big / small > 1.7) {
                double axis = 0.5 * Math.atan2(2 * sxy, sxx - syy);
                if (Math.abs(Math.cos(axis) * dx + Math.sin(axis) * dy) > 0.7) continue;
            }
            double out = Math.atan2(rc.y() - c.y(), rc.x() - c.x());
            Match asSign = SymbolRecognizer.classifySign(sym, out);
            if (asSign.template() == null || asSign.score() < SURE_SIGN) continue;
            Match whole = SymbolRecognizer.classifySign(pick(strokes, g), out);
            if (whole.template() != null && asSign.score() <= whole.score() + 0.1) continue;
            Match wholeSigil = center(pick(strokes, g), turn);
            if (wholeSigil.template() != null && wholeSigil.score() + 0.1 >= asSign.score()) continue;
            return true;
        }
        return false;
    }

    private static double segmentDist(Pt p, Pt a, Pt b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), l2 = dx * dx + dy * dy;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / l2));
        return Math.hypot(a.x() + dx * t - p.x(), a.y() + dy * t - p.y());
    }

    private static boolean closerThan(List<Pt> a, List<Pt> b, double limit) {
        double l2 = limit * limit;
        for (Pt p : a) for (Pt q : b) {
            double dx = p.x() - q.x(), dy = p.y() - q.y();
            if (dx * dx + dy * dy < l2) return true;
        }
        return false;
    }

    private static double minDist(List<Pt> a, List<Pt> b) {
        double min = Double.POSITIVE_INFINITY;
        for (Pt p : a) for (Pt q : b) min = Math.min(min, p.dist(q));
        return min;
    }

    private static List<List<Pt>> pick(List<List<Pt>> strokes, List<Integer> idx) {
        List<List<Pt>> out = new ArrayList<>();
        for (int i : idx) out.add(strokes.get(i));
        return out;
    }

    private static Pt centroid(List<List<Pt>> strokes, List<Integer> idx) {
        double x = 0, y = 0;
        int n = 0;
        for (int i : idx) for (Pt p : strokes.get(i)) { x += p.x(); y += p.y(); n++; }
        return new Pt(x / n, y / n);
    }

    private static double bboxDiagonal(List<List<Pt>> sym) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (List<Pt> s : sym) for (Pt p : s) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
        }
        return Math.hypot(maxX - minX, maxY - minY);
    }
}
