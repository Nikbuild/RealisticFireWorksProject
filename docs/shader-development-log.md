# Realistic Fireworks Shader Development Log

## Goal
Make sparkler sparks look like a real sparkler photo - thin bright radiating lines with soft glow, not chunky geometric polygon shapes.

![Target Look](reference: real sparkler has thin white-hot core at center with many thin bright lines radiating outward, golden/warm color fading at tips)

---

## What We Tried

### 1. Basic Bloom Shader (Post-Process Blur)
**Approach:** Extract bright pixels → Gaussian blur → Composite back onto scene

**Files Created:**
- `composite.fsh` - Bloom extraction
- `composite1.fsh` - Horizontal blur
- `composite2.fsh` - Vertical blur
- `final.fsh` - Combine bloom with scene

**Result:** ❌ Failed - Just blurred the chunky polygons. Blur can't turn rectangles into thin lines.

---

### 2. Thinner Quads in Java
**Approach:** Reduce the width of rendered quads in `SparkLineRenderer.java`

**Changes:**
- Reduced `width * 0.3f` instead of full width
- Reduced tail width to `thinWidth * 0.05f`
- Reduced tail alpha to `a * 0.02f`

**Result:** ❌ Failed - Still rendered as polygons, just slightly smaller. The crossed-quad approach (two planes at 90°) creates diamond shapes when viewed at angles.

---

### 3. Single Quad Instead of Crossed Quads
**Approach:** Remove the second crossed plane to eliminate diamond artifact

**Changes:**
- Removed the second perpendicular plane from `renderWireSpark()`
- Kept only one plane per spark

**Result:** ❌ Failed - Still chunky, just fewer polygons. Fundamental issue is quads are still too large.

---

### 4. Tiny Bright Points + Shader Bloom
**Approach:** Java renders tiny dots, shader creates glow around them

**Changes:**
- `SparkLineRenderer.java` - Render 0.003f size points
- Cap trail length to 0.04f
- Strong bloom shader with lower threshold (0.5)

**Result:** ❌ Failed - Points still rendered as visible squares. Bloom made them glow but didn't create radiating lines.

---

### 5. Radial Starburst Effect in Shader
**Approach:** Shader detects bright pixels and draws radiating streaks FROM them

**Changes:**
- `composite.fsh` - For each pixel, search in 32 directions for bright sources
- Draw streak color with distance-based falloff
- Random length variation per ray

**Result:** ❌ Partial - Effect worked but applied to EVERYTHING bright (sky, sun, etc.), not just sparkler.

---

### 6. gbuffers_textured_lit to Mark Emissive Pixels
**Approach:** Use particle shader to write spark data to separate buffer (colortex1)

**Changes:**
- Created `gbuffers_textured_lit.vsh/fsh`
- Write bright pixels to colortex1
- Composite reads only from colortex1

**Result:** ❌ Failed - Sparkler doesn't use particle render type. It uses `RenderType.lightning()` which goes through entities shader.

---

### 7. Alpha Marker Detection
**Approach:** Java renders sparks with specific alpha value (242/255 = 0.949), shader detects this

**Changes:**
- `SparkLineRenderer.java` - Use `SPARK_MARKER_ALPHA = 242`
- `gbuffers_textured_lit.fsh` - Check for alpha ≈ 0.949

**Result:** ❌ Failed - Detected everything EXCEPT sparkler (inverted). Confirmed sparkler uses different render path.

---

### 8. gbuffers_entities for Lightning RenderType
**Approach:** Create entity shader to catch `RenderType.lightning()`

**Changes:**
- Created `gbuffers_entities.vsh/fsh`
- Same alpha marker detection

**Result:** ❌ Failed - Broke entire scene rendering (everything white). Entity shader affects too much.

---

### 9. Color-Based Detection (No gbuffers)
**Approach:** Detect sparks by color signature: bright + warm + not pure white

**Changes:**
- Removed all gbuffers overrides
- `composite.fsh` - Check `brightness > 0.9 && warmth > 0.5 && blue < 0.95`

**Result:** ⚠️ Partial - Scene rendering restored, some glow on sparks, but still chunky polygon shapes underneath.

---

## What We Haven't Tried

### A. GL_LINES Primitive
**Idea:** Use actual OpenGL line primitives instead of quads
**Challenge:** Minecraft's `VertexConsumer` may not support GL_LINES. Would need custom RenderType.
**Potential:** High - lines are inherently 1-pixel wide

### B. Line Texture on Billboards
**Idea:** Create a texture image of a thin bright line, render camera-facing billboards
**Challenge:** Need to create texture asset, billboards need to rotate to face camera
**Potential:** Medium - still quads but with line appearance

### C. Custom RenderType with Line Mode
**Idea:** Create custom `RenderType` that uses `GL_LINES` or `GL_LINE_STRIP`
**Challenge:** Deep Minecraft rendering knowledge required
**Potential:** High - proper solution

### D. Screen-Space Line Drawing in Shader
**Idea:** Pass spark start/end positions to shader, draw lines entirely in screen space
**Challenge:** Need to pass position data (SSBO or encode in texture)
**Potential:** High - full control over line appearance

### E. Particle System Instead of Custom Rendering
**Idea:** Use Minecraft's built-in particle system which handles small points better
**Challenge:** May lose control over spark behavior/physics
**Potential:** Medium - particles are designed for this

### F. Geometry Shader for Line Expansion
**Idea:** Use geometry shader to convert points to screen-aligned lines
**Challenge:** Geometry shaders have compatibility issues, not all GPUs support well
**Potential:** Medium - elegant but risky

### G. Instanced Rendering with Line Mesh
**Idea:** Create a line mesh once, instance it for each spark
**Challenge:** Complex setup, need instancing support
**Potential:** Medium

### H. Depth-Based Masking
**Idea:** Render sparks at specific depth, shader only affects that depth range
**Challenge:** Depth values vary with distance, hard to isolate
**Potential:** Low

### I. Stencil Buffer Marking
**Idea:** Render sparks with stencil write, shader reads stencil to identify spark pixels
**Challenge:** Stencil buffer access in Iris shaders is limited
**Potential:** Medium

### ~~J. Separate Render Pass to Texture~~ (Lower priority)
**Idea:** Render sparks to a completely separate framebuffer, composite in shader
**Challenge:** Need to set up additional render target in mod code
**Potential:** High - cleanest separation

---

### 17. Tapered Triangles (Light Ray Style)
**Approach:** Render sparks as tapered triangles - wide base at head, converging to point at tail - with multiple planes at 60° intervals

**Implementation:**
- Triangle shape: 2 vertices at head (forming wide base), 1 vertex at tail (point)
- Uses degenerate quad (4th vertex same as 3rd) for QUADS mode
- 3 planes rotated at 0°, 60°, 120° around spark axis
- Alpha: bright at head (base), fading toward tail (tip)
- Width: 0.008 primary, 0.004 secondary
- Both front and back faces rendered

**Key Code:**
```java
// Create perpendicular vectors rotated around spark axis
for (int plane = 0; plane < 3; plane++) {
    float angle = (float)(plane * Math.PI / 3.0); // 60° intervals
    // Rotate around spark axis using Rodrigues formula

    // Tapered triangle: head is wide, tail is point
    sparkConsumer.addVertex(poseMat, h1x, h1y, h1z).setColor(r,g,b,headA); // base left
    sparkConsumer.addVertex(poseMat, h2x, h2y, h2z).setColor(r,g,b,headA); // base right
    sparkConsumer.addVertex(poseMat, tx, ty, tz).setColor(r,g,b,tailA);    // tip
    sparkConsumer.addVertex(poseMat, tx, ty, tz).setColor(r,g,b,tailA);    // degenerate
}
```

**Result:** ⚠️ Partial
- Sparks visible as arrow/triangle shapes
- Multiple planes help visibility from more angles
- Glow effect at head (base) is visible
- BUT: Still renders as obvious geometric triangles/arrows
- Not thin radiating lines - looks like arrowheads or chevrons
- The tapered shape is too wide at the base
- 3 intersecting triangles create star-like artifacts

---

## ATTEMPTS SUMMARY (17 total)

| # | Approach | Result |
|---|----------|--------|
| 1-9 | Various shader detection methods | Couldn't isolate sparks |
| 10 | GL_LINES | Angle visibility issues |
| 11 | Billboard quads | Large polygons |
| 12 | Small point sprites | Diamonds not lines |
| 13 | XZ velocity streaks | Flat at angles |
| 14 | True 3D billboard streaks | Still flat rectangles |
| 15 | Dotted trail (multiple points) | Invisible |
| 16 | 3D cross (4 quads) | Still flat, still disappear |
| 17 | Tapered triangles (3 planes) | Arrow shapes, not thin lines |

**Core Problem:** RenderType.lightning() QUADS will ALWAYS be flat 2D surfaces that look like geometric shapes.

---

### 18. Native Minecraft Particles (CRIT particle spawning)
**Approach:** Spawn actual Minecraft particles at spark positions every frame

**Implementation:**
- Use `ParticleEngine.createParticle()` with CRIT particle type
- Spawn 8 particles along each primary spark trail, 5 for secondary
- Set lifetime to 1 tick (instant - shows 1 frame only)
- Custom color and size per particle

**Result:** ❌ Failed
- Particles spawn but not visible or in wrong position
- BEWLR render context doesn't have proper world coordinates
- Particles spawn somewhere in world space, not relative to item
- The spark world positions don't account for player/camera offset

---

### 19. Ultra-Thin Quads with Glow Points
**Approach:** Extremely thin quads (0.0015 units) with bright diamond points at head

**Result:** ❌ Failed
- Still shows rectangular/diamond shapes
- The glow points are visible but still geometric
- Screenshot shows white rectangles with bright spots
- Quads are still visible as flat surfaces

---

## ATTEMPT 20: Unique Color Marker + Shader Detection

**New Strategy (USER IDEA):**
Instead of trying to make geometry look like lines, use a UNIQUE COLOR that nothing in Minecraft uses, then have the SHADER detect it and apply bloom/glow effects.

**Key insight:**
- Java renders simple geometry with a specific RGB that doesn't exist naturally in MC
- Shader detects this exact color and replaces it with glowing line effect
- The shader can draw proper thin lines in screen space

**Implementation:**
1. Pick a unique color like pure magenta (1.0, 0.0, 1.0) or specific RGB
2. Render spark geometry with this marker color
3. Shader `composite.fsh` detects pixels with this exact color
4. Shader applies radial streak/bloom effect to those pixels only

---

## Current State

**Java Side:**
- `SparkLineRenderer.java` renders sparks as small quads with marker alpha (242)
- Uses `RenderType.lightning()`
- Sparks appear as chunky white/gray polygons

**Shader Side:**
- `composite.fsh` - Color-based detection with radial streak effect
- `final.fsh` - Simple passthrough
- No gbuffers overrides (vanilla rendering preserved)

**Visual Result:**
- Chunky polygon shapes with some yellow glow halos
- Not resembling real sparkler thin radiating lines

---

## Key Insight

The fundamental problem is that **no post-processing can turn a polygon into a line**. The shader can only work with what Java renders. If Java renders a 10-pixel-wide quad, the shader sees a 10-pixel-wide shape.

**The solution must come from the Java side** - either:
1. Render actual line primitives (GL_LINES)
2. Render with a line texture
3. Pass position data and let shader draw the lines from scratch

---

## Files Modified

```
src/main/java/com/nick/realisticfirework/client/
├── SparkLineRenderer.java (multiple iterations)

shaderpack/RealisticFireworks/shaders/
├── shaders.properties
├── composite.fsh
├── composite.vsh
├── composite1.fsh (deleted)
├── composite2.fsh (deleted)
├── final.fsh
├── final.vsh
├── gbuffers_textured_lit.fsh (deleted)
├── gbuffers_textured_lit.vsh (deleted)
├── gbuffers_entities.fsh (deleted)
├── gbuffers_entities.vsh (deleted)
```

---

---

### 10. Custom RenderType with GL_LINES
**Approach:** Create custom `RenderType` using `VertexFormat.Mode.LINES` for true line primitives

**Files Created:**
- `SparkRenderTypes.java` - Custom RenderType with GL_LINES mode, 2-pixel width
- Updated `SparklerBEWLR.java` - Third render pass using line consumer
- Updated `SparkLineRenderer.java` - Added `renderSparkLine()` for line primitives

**Result:** ⚠️ Partial failure
- Lines render but only visible at certain angles
- Glow effect works but lines disappear when viewed from side
- GL_LINES have view-angle issues in 3D space
- Still doesn't look like real sparkler radiating lines

---

## What We Haven't Tried

### A. ~~GL_LINES Primitive~~ (TRIED - angle issues)

### B. Line Texture on Billboards
**Idea:** Create a texture image of a thin bright line, render camera-facing billboards
**Challenge:** Need to create texture asset, billboards need to rotate to face camera
**Potential:** Medium - still quads but with line appearance

### C. ~~Custom RenderType with Line Mode~~ (TRIED - angle issues)

### D. Screen-Space Line Drawing in Shader
**Idea:** Pass spark start/end positions to shader, draw lines entirely in screen space
**Challenge:** Need to pass position data (SSBO or encode in texture)
**Potential:** High - full control over line appearance

### E. Particle System Instead of Custom Rendering
**Idea:** Use Minecraft's built-in particle system which handles small points better
**Challenge:** May lose control over spark behavior/physics
**Potential:** Medium - particles are designed for this

### F. Geometry Shader for Line Expansion
**Idea:** Use geometry shader to convert points to screen-aligned lines
**Challenge:** Geometry shaders have compatibility issues, not all GPUs support well
**Potential:** Medium - elegant but risky

### G. Billboard Quads That Always Face Camera
**Idea:** Render thin quads that rotate to always face the camera (like Minecraft particles do)
**Challenge:** Need camera position, calculate billboard orientation per spark
**Potential:** HIGH - this is how most games do spark/beam effects

### H. Depth-Based Masking
**Idea:** Render sparks at specific depth, shader only affects that depth range
**Challenge:** Depth values vary with distance, hard to isolate
**Potential:** Low

### I. Stencil Buffer Marking
**Idea:** Render sparks with stencil write, shader reads stencil to identify spark pixels
**Challenge:** Stencil buffer access in Iris shaders is limited
**Potential:** Medium

### ~~J. Separate Render Pass to Texture~~ (Lower priority)
**Idea:** Render sparks to a completely separate framebuffer, composite in shader
**Challenge:** Need to set up additional render target in mod code
**Potential:** High - cleanest separation

---

### 17. Tapered Triangles (Light Ray Style)
**Approach:** Render sparks as tapered triangles - wide base at head, converging to point at tail - with multiple planes at 60° intervals

**Implementation:**
- Triangle shape: 2 vertices at head (forming wide base), 1 vertex at tail (point)
- Uses degenerate quad (4th vertex same as 3rd) for QUADS mode
- 3 planes rotated at 0°, 60°, 120° around spark axis
- Alpha: bright at head (base), fading toward tail (tip)
- Width: 0.008 primary, 0.004 secondary
- Both front and back faces rendered

**Key Code:**
```java
// Create perpendicular vectors rotated around spark axis
for (int plane = 0; plane < 3; plane++) {
    float angle = (float)(plane * Math.PI / 3.0); // 60° intervals
    // Rotate around spark axis using Rodrigues formula

    // Tapered triangle: head is wide, tail is point
    sparkConsumer.addVertex(poseMat, h1x, h1y, h1z).setColor(r,g,b,headA); // base left
    sparkConsumer.addVertex(poseMat, h2x, h2y, h2z).setColor(r,g,b,headA); // base right
    sparkConsumer.addVertex(poseMat, tx, ty, tz).setColor(r,g,b,tailA);    // tip
    sparkConsumer.addVertex(poseMat, tx, ty, tz).setColor(r,g,b,tailA);    // degenerate
}
```

**Result:** ⚠️ Partial
- Sparks visible as arrow/triangle shapes
- Multiple planes help visibility from more angles
- Glow effect at head (base) is visible
- BUT: Still renders as obvious geometric triangles/arrows
- Not thin radiating lines - looks like arrowheads or chevrons
- The tapered shape is too wide at the base
- 3 intersecting triangles create star-like artifacts

---

## ATTEMPTS SUMMARY (17 total)

| # | Approach | Result |
|---|----------|--------|
| 1-9 | Various shader detection methods | Couldn't isolate sparks |
| 10 | GL_LINES | Angle visibility issues |
| 11 | Billboard quads | Large polygons |
| 12 | Small point sprites | Diamonds not lines |
| 13 | XZ velocity streaks | Flat at angles |
| 14 | True 3D billboard streaks | Still flat rectangles |
| 15 | Dotted trail (multiple points) | Invisible |
| 16 | 3D cross (4 quads) | Still flat, still disappear |
| 17 | Tapered triangles (3 planes) | Arrow shapes, not thin lines |

**Core Problem:** RenderType.lightning() QUADS will ALWAYS be flat 2D surfaces that look like geometric shapes.

---

## ATTEMPT 18: Textured Line Sprite (Using Minecraft's Particle System Properly)

**New Strategy:**
Instead of fighting with geometry, use Minecraft's PARTICLE system which is DESIGNED for this:
1. Spawn actual particle entities from World, not render in BEWLR
2. Particles use a sprite sheet with proper alpha transparency
3. Minecraft handles billboarding automatically
4. Can use motion blur / streak textures

**Why this is different:**
- BEWLR renders in "item space" which is tricky for world-oriented effects
- Minecraft particles render in world space with proper billboarding
- Particle sprites can have any shape via alpha channel
- Built-in velocity-based stretching available

---

### 11. Billboard Quads That Always Face Camera
**Approach:** Render sparks as thin quads that always face the camera, ensuring visibility from all angles

**Files Created:**
- `BillboardSparkRenderer.java` - Camera-facing billboard quad renderer with glow layers

**Implementation:**
1. Get camera position from Minecraft's game renderer
2. For each spark, calculate view direction from camera to spark midpoint
3. Cross product of line direction × view direction = perpendicular axis for width
4. Create thin quad with vertices offset along perpendicular axis
5. Multiple overlapping layers with decreasing alpha for glow effect:
   - Core layer: 50% width, bright
   - Middle layer: 100% width, medium brightness
   - Outer glow: 200% width, dim

**Key Code:**
```java
// Cross product to get billboard perpendicular direction
float perpX = lineY * viewZ - lineZ * viewY;
float perpY = lineZ * viewX - lineX * viewZ;
float perpZ = lineX * viewY - lineY * viewX;

// Quad corners offset by perpendicular × width
float hlX = headModel.x - perpX * width;
float hrX = headModel.x + perpX * width;
// ... render quad
```

**Result:** ❌ Failed
- Sparks render as large triangular/diamond shapes instead of thin lines
- The quads are WAY too wide - not thin lines at all
- RenderType.lightning() uses QUADS mode which creates filled polygons
- The perpendicular width calculation creates large shapes instead of thin lines
- Multiple overlapping glow layers made it worse (more geometry)
- Still doesn't look like radiating spark lines

---

### 12. Small Axis-Aligned Point Sprites
**Approach:** Render each spark as a tiny axis-aligned quad at head position only

**Implementation:**
- Each spark rendered as small quad (0.012 units for primary, 0.006 for secondary)
- Positioned at spark head only (not head-to-tail line)
- Uses lightning render type for additive blending

**Result:** ⚠️ Partial success
- Sparks are now small diamond shapes (much better than huge triangles!)
- But still diamond/square shaped, not thin radiating lines
- The glow effect at burn point looks good
- Individual sparks visible but not line-like
- Need thin elongated shapes that follow spark trajectory

---

### 13. Velocity-Aligned Streak Quads (XZ perpendicular only)
**Approach:** Render elongated quads from head to tail, perpendicular calculated in XZ plane only

**Implementation:**
- Width very thin (0.003 units primary, 0.0015 secondary)
- Perpendicular direction: `perpX = -dz/len*width`, `perpZ = dx/len*width`
- Alpha fades from head (100%) to tail (20%)

**Result:** ⚠️ Partial success
- Sparks are now elongated LINE shapes - major improvement!
- They follow the spark trajectory correctly
- BUT: At certain viewing angles they appear flat/wide
- Problem: Perpendicular only in XZ plane, ignores Y component
- Need true 3D camera-facing perpendicular calculation

---

### 14. Camera-Facing Velocity Streaks (True 3D Billboard)
**Approach:** Calculate perpendicular using cross product with camera view direction

**Implementation:**
- Get camera position, transform to model space
- For each spark: line direction = head - tail (normalized)
- View direction = spark midpoint - camera (normalized)
- Perpendicular = cross(line, view) - always faces camera
- Scale perpendicular by width (0.004 primary, 0.002 secondary)

**Result:** ❌ Failed
- Still renders as flat rectangles
- The rectangles ARE camera-facing now, but they're still rectangles
- **FUNDAMENTAL PROBLEM**: RenderType.lightning() uses QUADS = flat surfaces
- No amount of orientation math can make a flat quad look like a thin line
- Need a different rendering primitive entirely

---

## FUNDAMENTAL INSIGHT

**The core issue:** We've been trying to make QUADS look like LINES. This is impossible.
- Quads are 2D surfaces with area
- Lines are 1D with no area
- A quad viewed edge-on disappears; a line doesn't

**What real sparkler effects need:**
1. TRUE 1-pixel lines (GL_LINES) - but these have driver issues
2. Post-processing bloom on tiny points - needs shader detection
3. Procedural line drawing in fragment shader
4. Pre-rendered line texture with alpha transparency

---

## What We Haven't Tried (Updated)

### ~~A. GL_LINES Primitive~~ (TRIED - angle issues)
### ~~G. Billboard Quads That Always Face Camera~~ (TRIED - creates large polygons)
### ~~12. Small Point Sprites~~ (TRIED - diamonds, not lines)
### ~~13. XZ-Only Velocity Streaks~~ (TRIED - flat at angles)
### ~~14. True 3D Camera-Facing Streaks~~ (TRIED - still flat rectangles)
### ~~15. Dotted Trail (Multiple Points Per Spark)~~ (TRIED - invisible at angles)

---

### 15. Dotted Trail Effect
**Approach:** Render 6 tiny camera-facing billboards along head-to-tail path

**Implementation:**
- For each spark, render 6 points interpolated from head to tail
- Each point is tiny billboard (0.008 units)
- Alpha/size decrease toward tail
- Billboard calculated using up×view cross product

**Result:** ❌ Failed
- Sparks almost completely invisible
- Only the glow sphere at burn point visible
- The tiny billboards still disappear at certain camera angles
- Billboarding math may be wrong, or points too small to see

---

### 16. 3D Cross (4 World-Axis-Aligned Quads Per Spark)
**Approach:** Render 4 perpendicular quads along spark line (like grass/foliage technique)

**Implementation:**
- Quad 1: Width along X axis
- Quad 2: Width along Y axis
- Quad 3: Width along Z axis
- Quad 4: Another variant
- Idea: At least one quad should face camera from any angle

**Result:** ❌ Failed
- Still renders as flat rectangles
- Sparks still disappear at certain angles
- Problem: Quads aligned to WORLD axes, not SPARK direction
- The perpendicular directions don't actually create proper visibility

---

## ATTEMPTS SUMMARY (16 total failures)

| # | Approach | Result |
|---|----------|--------|
| 1-9 | Various shader detection methods | Couldn't isolate sparks |
| 10 | GL_LINES | Angle visibility issues |
| 11 | Billboard quads | Large polygons |
| 12 | Small point sprites | Diamonds not lines |
| 13 | XZ velocity streaks | Flat at angles |
| 14 | True 3D billboard streaks | Still flat rectangles |
| 15 | Dotted trail (multiple points) | Invisible |
| 16 | 3D cross (4 quads) | Still flat, still disappear |

**Core Problem:** RenderType.lightning() QUADS will ALWAYS be flat 2D surfaces.

---

### B. Line Texture on Billboards
**Idea:** Create a texture image of a thin bright line, render camera-facing billboards
**Challenge:** Need to create texture asset, billboards need to rotate to face camera
**Potential:** Medium - still quads but with line appearance

### D. Screen-Space Line Drawing in Shader
**Idea:** Pass spark start/end positions to shader, draw lines entirely in screen space
**Challenge:** Need to pass position data (SSBO or encode in texture)
**Potential:** High - full control over line appearance

### E. Particle System Instead of Custom Rendering
**Idea:** Use Minecraft's built-in particle system which handles small points better
**Challenge:** May lose control over spark behavior/physics
**Potential:** Medium - particles are designed for this

### F. Geometry Shader for Line Expansion
**Idea:** Use geometry shader to convert points to screen-aligned lines
**Challenge:** Geometry shaders have compatibility issues, not all GPUs support well
**Potential:** Medium - elegant but risky

### H. Depth-Based Masking
**Idea:** Render sparks at specific depth, shader only affects that depth range
**Challenge:** Depth values vary with distance, hard to isolate
**Potential:** Low

### I. Stencil Buffer Marking
**Idea:** Render sparks with stencil write, shader reads stencil to identify spark pixels
**Challenge:** Stencil buffer access in Iris shaders is limited
**Potential:** Medium

### ~~J. Separate Render Pass to Texture~~ (Lower priority)
**Idea:** Render sparks to a completely separate framebuffer, composite in shader
**Challenge:** Need to set up additional render target in mod code
**Potential:** High - cleanest separation

---

### 17. Tapered Triangles (Light Ray Style)
**Approach:** Render sparks as tapered triangles - wide base at head, converging to point at tail - with multiple planes at 60° intervals

**Implementation:**
- Triangle shape: 2 vertices at head (forming wide base), 1 vertex at tail (point)
- Uses degenerate quad (4th vertex same as 3rd) for QUADS mode
- 3 planes rotated at 0°, 60°, 120° around spark axis
- Alpha: bright at head (base), fading toward tail (tip)
- Width: 0.008 primary, 0.004 secondary
- Both front and back faces rendered

**Key Code:**
```java
// Create perpendicular vectors rotated around spark axis
for (int plane = 0; plane < 3; plane++) {
    float angle = (float)(plane * Math.PI / 3.0); // 60° intervals
    // Rotate around spark axis using Rodrigues formula

    // Tapered triangle: head is wide, tail is point
    sparkConsumer.addVertex(poseMat, h1x, h1y, h1z).setColor(r,g,b,headA); // base left
    sparkConsumer.addVertex(poseMat, h2x, h2y, h2z).setColor(r,g,b,headA); // base right
    sparkConsumer.addVertex(poseMat, tx, ty, tz).setColor(r,g,b,tailA);    // tip
    sparkConsumer.addVertex(poseMat, tx, ty, tz).setColor(r,g,b,tailA);    // degenerate
}
```

**Result:** ⚠️ Partial
- Sparks visible as arrow/triangle shapes
- Multiple planes help visibility from more angles
- Glow effect at head (base) is visible
- BUT: Still renders as obvious geometric triangles/arrows
- Not thin radiating lines - looks like arrowheads or chevrons
- The tapered shape is too wide at the base
- 3 intersecting triangles create star-like artifacts

---

## ATTEMPTS SUMMARY (17 total)

| # | Approach | Result |
|---|----------|--------|
| 1-9 | Various shader detection methods | Couldn't isolate sparks |
| 10 | GL_LINES | Angle visibility issues |
| 11 | Billboard quads | Large polygons |
| 12 | Small point sprites | Diamonds not lines |
| 13 | XZ velocity streaks | Flat at angles |
| 14 | True 3D billboard streaks | Still flat rectangles |
| 15 | Dotted trail (multiple points) | Invisible |
| 16 | 3D cross (4 quads) | Still flat, still disappear |
| 17 | Tapered triangles (3 planes) | Arrow shapes, not thin lines |

**Core Problem:** RenderType.lightning() QUADS will ALWAYS be flat 2D surfaces that look like geometric shapes.

---

## ATTEMPT 18: Textured Line Sprite (Using Minecraft's Particle System Properly)

**New Strategy:**
Instead of fighting with geometry, use Minecraft's PARTICLE system which is DESIGNED for this:
1. Spawn actual particle entities from World, not render in BEWLR
2. Particles use a sprite sheet with proper alpha transparency
3. Minecraft handles billboarding automatically
4. Can use motion blur / streak textures

**Why this is different:**
- BEWLR renders in "item space" which is tricky for world-oriented effects
- Minecraft particles render in world space with proper billboarding
- Particle sprites can have any shape via alpha channel
- Built-in velocity-based stretching available


---

## ATTEMPT 20: Per-Spark Light Emission + Ultra-Thin Geometry

### 20a. Per-Spark Radial Glow (SHADER)

**User Feedback:** "the sparks need to have their own light source each"

**Implementation:**
Each spark marker (magenta pixel) emits radial glow in a 25-pixel radius around it. The shader searches in 8 directions for spark markers and adds light contribution from each found spark.

**Key Shader Code (composite.fsh):**
```glsl
// Each spark emits its own glow
vec3 sparkLight = vec3(0.0);
float sparkGlowRadius = 25.0;

for (int i = 0; i < 8; i++) {
    float angle = float(i) * 0.785398;
    vec2 dir = vec2(cos(angle), sin(angle));

    for (float d = 1.0; d < sparkGlowRadius; d += 1.5) {
        vec2 samplePos = texcoord + dir * texelSize * d;
        vec4 s = texture(colortex0, samplePos);

        if (isSparkMarker(s.rgb)) {
            float falloff = 1.0 - (d / sparkGlowRadius);
            falloff = pow(falloff, 1.5);
            float sparkIntensity = s.a;

            // Color gradient: white → golden
            vec3 glowCol = mix(white, golden, d / sparkGlowRadius);
            sparkLight += glowCol * falloff * sparkIntensity * 0.35;
        }
    }
}
color.rgb += sparkLight;
```

**Result:** ✅ SUCCESS - User: "i do like that they have their own light"

---

### 20b. Ultra-Thin Spark Geometry (JAVA)

**User Feedback:** "the sparks need to be way thinner"

**Changes to SparklerBEWLR.java:**
- `lineWidth`: 0.004f/0.002f → **0.001f/0.0005f** (4x thinner)
- `pointSize`: 0.008f/0.005f → **0.002f/0.001f** (4x smaller)

**Result:** PENDING TEST

---

## Current Architecture Summary

| Component | Value |
|-----------|-------|
| Marker Color | Magenta (1.0, 0.0, 1.0) |
| Primary lineWidth | 0.001f |
| Secondary lineWidth | 0.0005f |
| Primary pointSize | 0.002f |
| Secondary pointSize | 0.001f |
| Spark Glow Radius | 25 pixels |
| Burn Point Glow Radius | 60 pixels |
| Starburst Rays | 12 |
