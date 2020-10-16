package com.mapbox.navigation.ui.route

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import androidx.test.core.app.ApplicationProvider
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.core.constants.Constants
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.location.LocationComponentConstants
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.Layer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyValue
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.navigation.ui.R
import com.mapbox.navigation.ui.internal.ThemeSwitcher
import com.mapbox.navigation.ui.internal.route.MapRouteSourceProvider
import com.mapbox.navigation.ui.internal.route.RouteConstants
import com.mapbox.navigation.ui.internal.route.RouteConstants.ALTERNATIVE_ROUTE_CASING_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.ALTERNATIVE_ROUTE_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.PRIMARY_ROUTE_CASING_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.PRIMARY_ROUTE_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.PRIMARY_ROUTE_TRAFFIC_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteConstants.WAYPOINT_LAYER_ID
import com.mapbox.navigation.ui.internal.route.RouteLayerProvider
import com.mapbox.navigation.ui.route.MapRouteLine.MapRouteLineSupport.calculatePreciseDistanceTraveledAlongLine
import com.mapbox.navigation.ui.route.MapRouteLine.MapRouteLineSupport.getRouteLineExpressionDataWithStreetClassOverride
import com.mapbox.navigation.ui.route.MapRouteLine.MapRouteLineSupport.getRouteLineTrafficExpressionData
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import junit.framework.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.robolectric.RobolectricTestRunner
import java.util.Scanner

@RunWith(RobolectricTestRunner::class)
class MapRouteLineTest {

    lateinit var ctx: Context
    var styleRes: Int = 0
    lateinit var wayPointSource: GeoJsonSource
    lateinit var primaryRouteLineSource: GeoJsonSource
    lateinit var primaryRouteCasingSource: GeoJsonSource
    lateinit var primaryRouteLineTrafficSource: GeoJsonSource
    lateinit var alternativeRouteLineSource: GeoJsonSource

    lateinit var mapRouteSourceProvider: MapRouteSourceProvider
    lateinit var layerProvider: RouteLayerProvider
    lateinit var alternativeRouteCasingLayer: LineLayer
    lateinit var alternativeRouteLayer: LineLayer
    lateinit var primaryRouteCasingLayer: LineLayer
    lateinit var primaryRouteLayer: LineLayer
    lateinit var primaryRouteTrafficLayer: LineLayer
    lateinit var waypointLayer: SymbolLayer

    lateinit var style: Style

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        styleRes = ThemeSwitcher.retrieveAttrResourceId(
            ctx,
            R.attr.navigationViewRouteStyle,
            R.style.MapboxStyleNavigationMapRoute
        )
        alternativeRouteCasingLayer = mockk {
            every { id } returns ALTERNATIVE_ROUTE_CASING_LAYER_ID
        }

        alternativeRouteLayer = mockk {
            every { id } returns ALTERNATIVE_ROUTE_LAYER_ID
        }

        primaryRouteCasingLayer = mockk(relaxUnitFun = true) {
            every { id } returns PRIMARY_ROUTE_CASING_LAYER_ID
        }

        primaryRouteLayer = mockk(relaxUnitFun = true) {
            every { id } returns PRIMARY_ROUTE_LAYER_ID
        }

        waypointLayer = mockk {
            every { id } returns WAYPOINT_LAYER_ID
        }

        primaryRouteTrafficLayer = mockk(relaxUnitFun = true) {
            every { id } returns PRIMARY_ROUTE_TRAFFIC_LAYER_ID
        }

        style = mockk(relaxUnitFun = true) {
            every { getLayer(ALTERNATIVE_ROUTE_LAYER_ID) } returns alternativeRouteLayer
            every { getLayer(ALTERNATIVE_ROUTE_CASING_LAYER_ID) } returns
                alternativeRouteCasingLayer
            every { getLayer(PRIMARY_ROUTE_LAYER_ID) } returns primaryRouteLayer
            every { getLayer(PRIMARY_ROUTE_TRAFFIC_LAYER_ID) } returns primaryRouteTrafficLayer
            every { getLayer(PRIMARY_ROUTE_CASING_LAYER_ID) } returns primaryRouteCasingLayer
            every { getLayer(WAYPOINT_LAYER_ID) } returns waypointLayer
            every { isFullyLoaded } returns false
        }

        wayPointSource = mockk(relaxUnitFun = true)
        primaryRouteLineSource = mockk(relaxUnitFun = true)
        primaryRouteCasingSource = mockk(relaxUnitFun = true)
        primaryRouteLineTrafficSource = mockk(relaxUnitFun = true)
        alternativeRouteLineSource = mockk(relaxUnitFun = true)

        mapRouteSourceProvider = mockk {
            every { build(RouteConstants.WAYPOINT_SOURCE_ID, any(), any()) } returns wayPointSource
            every {
                build(
                    RouteConstants.PRIMARY_ROUTE_SOURCE_ID,
                    any(),
                    any()
                )
            } returns primaryRouteLineSource
            every {
                build(
                    RouteConstants.ALTERNATIVE_ROUTE_SOURCE_ID,
                    any(),
                    any()
                )
            } returns alternativeRouteLineSource
        }
        layerProvider = mockk {
            every {
                initializeAlternativeRouteCasingLayer(
                    style,
                    -9273715
                )
            } returns alternativeRouteCasingLayer
            every {
                initializeAlternativeRouteLayer(
                    style,
                    true,
                    -7957339
                )
            } returns alternativeRouteLayer
            every {
                initializePrimaryRouteCasingLayer(
                    style,
                    -13665594
                )
            } returns primaryRouteCasingLayer
            every {
                initializePrimaryRouteLayer(
                    style,
                    true,
                    -11097861
                )
            } returns primaryRouteLayer
            every { initializeWayPointLayer(style, any(), any()) } returns waypointLayer
            every {
                initializePrimaryRouteTrafficLayer(
                    style,
                    true,
                    -11097861
                )
            } returns primaryRouteTrafficLayer
        }
    }

    @Test
    fun getStyledColor() {
        val result = MapRouteLine.MapRouteLineSupport.getStyledColor(
            R.styleable.MapboxStyleNavigationMapRoute_routeColor,
            R.color.mapbox_navigation_route_layer_blue,
            ctx,
            styleRes
        )

        assertEquals(-11097861, result)
    }

    @Test
    fun getPrimaryRoute() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.getPrimaryRoute()

        assertEquals(result, directionsRoute)
    }

    @Test
    fun getLineStringForRoute() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.getLineStringForRoute(directionsRoute)

        assertEquals(result.coordinates().size, 4)
    }

    @Test
    fun getLineStringForRouteWhenCalledWithUnknownRoute() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val directionsRoute2: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.getLineStringForRoute(directionsRoute2)

        assertNotNull(result)
    }

    @Test
    fun retrieveRouteFeatureData() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.retrieveRouteFeatureData()

        assertEquals(result.size, 1)
        assertEquals(result[0].route, directionsRoute)
    }

    @Test
    fun retrieveRouteLineStrings() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.retrieveRouteLineStrings()

        assertEquals(result.size, 1)
    }

    @Test
    fun retrieveDirectionsRoutes() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute)) }

        val result = mapRouteLine.retrieveDirectionsRoutes()

        assertEquals(result[0], directionsRoute)
    }

    @Test
    fun retrieveDirectionsRoutesPrimaryRouteIsFirstInList() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val primaryRoute: DirectionsRoute = getDirectionsRoute(true)
        val alternativeRoute: DirectionsRoute = getDirectionsRoute(false)
        val directionsRoutes = mutableListOf(primaryRoute, alternativeRoute)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(directionsRoutes) }
        directionsRoutes.reverse()

        val result = mapRouteLine.retrieveDirectionsRoutes()

        assertEquals(result[0], primaryRoute)
        assertEquals(2, result.size)
    }

    @Test
    fun retrieveDirectionsRoutesWhenPrimaryRouteIsNull() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val firstRoute: DirectionsRoute = getDirectionsRoute(true)
        val secondRoute: DirectionsRoute = getDirectionsRoute(false)
        val firstRouteFeatureCollection = mockk<FeatureCollection> {
            every { features() } returns listOf()
        }
        val secondRouteFeatureCollection = mockk<FeatureCollection> {
            every { features() } returns listOf()
        }
        val directionsRoutes = listOf(
            RouteFeatureData(firstRoute, firstRouteFeatureCollection, mockk<LineString>()),
            RouteFeatureData(secondRoute, secondRouteFeatureCollection, mockk<LineString>())
        )
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            directionsRoutes,
            listOf(),
            false,
            false,
            mapRouteSourceProvider,
            0f,
            null
        )

        val result = mapRouteLine.retrieveDirectionsRoutes()

        assertEquals(2, result.size)
    }

    @Test
    fun getTopLayerId() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result = mapRouteLine.getTopLayerId()

        assertEquals(result, "mapbox-navigation-waypoint-layer")
    }

    @Test
    fun updatePrimaryRouteIndex() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val directionsRoute: DirectionsRoute = getDirectionsRoute(true)
        val directionsRoute2: DirectionsRoute = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(directionsRoute, directionsRoute2)) }

        assertEquals(mapRouteLine.getPrimaryRoute(), directionsRoute)

        mapRouteLine.updatePrimaryRouteIndex(directionsRoute2)
        val result = mapRouteLine.getPrimaryRoute()

        assertEquals(result, directionsRoute2)
    }

    @Test
    fun getStyledColorRecyclesAttributes() {
        val context = mockk<Context>()
        val resources = mockk<Resources>()
        val typedArray = mockk<TypedArray>(relaxUnitFun = true)
        every {
            context.obtainStyledAttributes(
                styleRes,
                R.styleable.MapboxStyleNavigationMapRoute
            )
        } returns typedArray
        every { context.resources } returns resources
        every { context.getColor(R.color.mapbox_navigation_route_layer_blue) } returns 0
        every { resources.getColor(R.color.mapbox_navigation_route_layer_blue) } returns 0
        every {
            typedArray.getColor(
                R.styleable.MapboxStyleNavigationMapRoute_routeColor,
                anyInt()
            )
        } returns 0

        MapRouteLine.MapRouteLineSupport.getStyledColor(
            R.styleable.MapboxStyleNavigationMapRoute_routeColor,
            R.color.mapbox_navigation_route_layer_blue,
            context,
            styleRes
        )

        verify(exactly = 1) { typedArray.recycle() }
    }

    @Test
    fun getFloatStyledValue() {
        val result: Float = MapRouteLine.MapRouteLineSupport.getFloatStyledValue(
            R.styleable.MapboxStyleNavigationMapRoute_alternativeRouteScale,
            1.0f,
            ctx,
            styleRes
        )

        assertEquals(1.0f, result)
    }

    @Test
    fun getFloatStyledValueRecyclesAttributes() {
        val context = mockk<Context>()
        val typedArray = mockk<TypedArray>(relaxUnitFun = true)
        every {
            context.obtainStyledAttributes(
                styleRes,
                R.styleable.MapboxStyleNavigationMapRoute
            )
        } returns typedArray
        every {
            typedArray.getFloat(
                R.styleable.MapboxStyleNavigationMapRoute_alternativeRouteScale,
                1.0f
            )
        } returns 1.0f

        MapRouteLine.MapRouteLineSupport.getFloatStyledValue(
            R.styleable.MapboxStyleNavigationMapRoute_alternativeRouteScale,
            1.0f,
            context,
            styleRes
        )

        verify(exactly = 1) { typedArray.recycle() }
    }

    @Test
    fun getBooleanStyledValue() {
        val result = MapRouteLine.MapRouteLineSupport.getBooleanStyledValue(
            R.styleable.MapboxStyleNavigationMapRoute_roundedLineCap,
            true,
            ctx,
            styleRes
        )

        assertEquals(true, result)
    }

    @Test
    fun getBooleanStyledValueRecyclesAttributes() {
        val context = mockk<Context>()
        val typedArray = mockk<TypedArray>(relaxUnitFun = true)
        every {
            context.obtainStyledAttributes(
                styleRes,
                R.styleable.MapboxStyleNavigationMapRoute
            )
        } returns typedArray
        every {
            typedArray.getBoolean(
                R.styleable.MapboxStyleNavigationMapRoute_roundedLineCap,
                true
            )
        } returns true

        MapRouteLine.MapRouteLineSupport.getBooleanStyledValue(
            R.styleable.MapboxStyleNavigationMapRoute_roundedLineCap,
            true,
            context,
            styleRes
        )

        verify(exactly = 1) { typedArray.recycle() }
    }

    @Test
    fun getResourceStyledValue() {
        val result = MapRouteLine.MapRouteLineSupport.getResourceStyledValue(
            R.styleable.MapboxStyleNavigationMapRoute_originWaypointIcon,
            R.drawable.mapbox_ic_route_origin,
            ctx,
            styleRes
        )

        assertEquals(R.drawable.mapbox_ic_route_origin, result)
    }

    @Test
    fun getResourceStyledValueRecyclesAttributes() {
        val context = mockk<Context>()
        val typedArray = mockk<TypedArray>(relaxUnitFun = true)
        every {
            context.obtainStyledAttributes(
                styleRes,
                R.styleable.MapboxStyleNavigationMapRoute
            )
        } returns typedArray
        every {
            typedArray.getResourceId(
                R.styleable.MapboxStyleNavigationMapRoute_originWaypointIcon,
                R.drawable.mapbox_ic_route_origin
            )
        } returns R.drawable.mapbox_ic_route_origin

        MapRouteLine.MapRouteLineSupport.getResourceStyledValue(
            R.styleable.MapboxStyleNavigationMapRoute_originWaypointIcon,
            R.drawable.mapbox_ic_route_origin,
            context,
            styleRes
        )

        verify(exactly = 1) { typedArray.recycle() }
    }

    @Test
    fun getBelowLayerWithNullLayerId() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<Layer>()
        val layerCantaloupe = mockk<Layer>()
        val layerDragonfruit = mockk<SymbolLayer>()
        val layers = listOf(layerApple, layerBanana, layerCantaloupe, layerDragonfruit)
        every { style.layers } returns layers
        every { layerApple.id } returns "layerApple"
        every { layerBanana.id } returns RouteConstants.MAPBOX_LOCATION_ID
        every { layerCantaloupe.id } returns "layerCantaloupe"
        every { layerDragonfruit.id } returns "layerDragonfruit"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer(null, style)

        assertEquals("layerCantaloupe", result)
    }

    @Test
    fun getBelowLayerWithEmptyLayerId() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<Layer>()
        val layerCantaloupe = mockk<Layer>()
        val layerDragonfruit = mockk<SymbolLayer>()
        val layers = listOf(layerApple, layerBanana, layerCantaloupe, layerDragonfruit)
        every { style.layers } returns layers
        every { layerApple.id } returns "layerApple"
        every { layerBanana.id } returns RouteConstants.MAPBOX_LOCATION_ID
        every { layerCantaloupe.id } returns "layerCantaloupe"
        every { layerDragonfruit.id } returns "layerDragonfruit"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer("", style)

        assertEquals("layerCantaloupe", result)
    }

    @Test
    fun getBelowLayerReturnsShadowLayerIdAsDefault() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<SymbolLayer>()
        val layers = listOf(layerApple, layerBanana)
        every { style.layers } returns layers
        every { layerApple.id } returns RouteConstants.MAPBOX_LOCATION_ID
        every { layerBanana.id } returns "layerBanana"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer(null, style)

        assertEquals(LocationComponentConstants.SHADOW_LAYER, result)
    }

    @Test
    fun getBelowLayerReturnsInputIdIfFound() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<Layer>()
        val layerCantaloupe = mockk<Layer>()
        val layerDragonfruit = mockk<Layer>()
        val layers = listOf(layerApple, layerBanana, layerCantaloupe, layerDragonfruit)
        every { style.layers } returns layers
        every { layerApple.id } returns "layerApple"
        every { layerBanana.id } returns "layerBanana"
        every { layerCantaloupe.id } returns "layerCantaloupe"
        every { layerDragonfruit.id } returns "layerDragonfruit"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer("layerBanana", style)

        assertEquals("layerBanana", result)
    }

    @Test
    fun getBelowLayerReturnsShadowLayerIfInputNotNullOrEmptyAndNotFound() {
        val style = mockk<Style>()
        val layerApple = mockk<Layer>()
        val layerBanana = mockk<Layer>()
        val layerCantaloupe = mockk<Layer>()
        val layerDragonfruit = mockk<Layer>()
        val layers = listOf(layerApple, layerBanana, layerCantaloupe, layerDragonfruit)
        every { style.layers } returns layers
        every { layerApple.id } returns "layerApple"
        every { layerBanana.id } returns "layerBanana"
        every { layerCantaloupe.id } returns "layerCantaloupe"
        every { layerDragonfruit.id } returns "layerDragonfruit"

        val result = MapRouteLine.MapRouteLineSupport.getBelowLayer("foobar", style)

        assertEquals(LocationComponentConstants.SHADOW_LAYER, result)
    }

    @Test
    fun generateFeatureCollectionContainsRoute() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.generateFeatureCollection(route)

        assertEquals(route, result.route)
    }

    @Test
    fun generateFeatureLineStringContainsCorrectCoordinates() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.generateFeatureCollection(route)

        assertEquals(4, result.lineString.coordinates().size)
    }

    @Test
    fun generateFeatureFeatureCollectionContainsCorrectFeatures() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.generateFeatureCollection(route)

        assertEquals(1, result.featureCollection.features()!!.size)
    }

    @Test
    fun buildRouteLineExpression() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression =
            "[\"step\", [\"line-progress\"], [\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.2, [\"rgba\", " +
                "86.0, 168.0, 251.0, 1.0], 0.31436133, [\"rgba\", 86.0, 168.0, 251.0, 1.0], " +
                "0.92972755, [\"rgba\", 255.0, 77.0, 77.0, 1.0], 1.0003215, [\"rgba\", 86.0, " +
                "168.0, 251.0, 1.0]]"
        val route = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        val expression = mapRouteLine.getExpressionAtOffset(.2f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun buildRouteLineExpressionMultileg() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression = loadJsonFixture("build_route_line_expression_multileg_text.txt")
        val route = getMultilegRoute()
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        val expression = mapRouteLine.getExpressionAtOffset(0f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun buildRouteLineExpressionWhenNoTraffic() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression =
            "[\"step\", [\"line-progress\"], [\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.2, [\"rgba\", " +
                "86.0, 168.0, 251.0, 1.0]]"
        val route = getDirectionsRoute(false)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        val expression = mapRouteLine.getExpressionAtOffset(.2f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun buildRouteLineExpressionOffsetAfterLastLeg() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression =
            "[\"step\", [\"line-progress\"], [\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.9, " +
                "[\"rgba\", 86.0, 168.0, 251.0, 1.0]]"
        val route = getDirectionsRoute(false)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        val expression = mapRouteLine.getExpressionAtOffset(.9f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun calculateRouteLineSegmentsMultilegRoute() {
        val route = getMultilegRoute()
        val lineString = LineString.fromPolyline(route.geometry()!!, Constants.PRECISION_6)

        val result = MapRouteLine.MapRouteLineSupport.calculateRouteLineSegments(
            route,
            lineString,
            true
        ) { _, _ -> 1 }

        assertEquals(21, result.size)
    }

    @Test
    fun calculateRouteLineSegmentsMultilegRouteFirstDistanceValueAboveMinimumOffset() {
        val route = getMultilegRoute()
        val lineString = LineString.fromPolyline(route.geometry()!!, Constants.PRECISION_6)

        val result = MapRouteLine.MapRouteLineSupport.calculateRouteLineSegments(
            route,
            lineString,
            true
        ) { _, _ -> 1 }

        assertTrue(result[1].offset > .001f)
    }

    @Test
    fun calculateRouteLineSegmentFromCongestion() {
        val route = getMultilegRoute()
        val lineString = LineString.fromPolyline(route.geometry()!!, Constants.PRECISION_6)

        val result = MapRouteLine.MapRouteLineSupport.calculateRouteLineSegmentsFromCongestion(
            route.legs()!![0].annotation()!!.congestion()!!.toList(),
            lineString,
            route.distance()!!,
            true
        ) { _, _ -> 1 }

        assertEquals(9, result.size)
    }

    @Test
    fun buildWayPointFeatureCollection() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.buildWayPointFeatureCollection(route)

        assertEquals(2, result.features()!!.size)
    }

    @Test
    fun buildWayPointFeatureCollectionFirstFeatureOrigin() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.buildWayPointFeatureCollection(route)

        assertEquals("{\"wayPoint\":\"origin\"}", result.features()!![0].properties().toString())
    }

    @Test
    fun buildWayPointFeatureCollectionSecondFeatureOrigin() {
        val route = getDirectionsRoute(true)

        val result = MapRouteLine.MapRouteLineSupport.buildWayPointFeatureCollection(route)

        assertEquals(
            "{\"wayPoint\":\"destination\"}",
            result.features()!![1].properties().toString()
        )
    }

    @Test
    fun buildWayPointFeatureFromLeg() {
        val route = getDirectionsRoute(true)

        val result =
            MapRouteLine.MapRouteLineSupport.buildWayPointFeatureFromLeg(route.legs()!![0], 0)

        assertEquals(-122.523514, (result!!.geometry() as Point).coordinates()[0], 0.0)
        assertEquals(37.975355, (result.geometry() as Point).coordinates()[1], 0.0)
    }

    @Test
    fun buildWayPointFeatureFromLegContainsOriginWaypoint() {
        val route = getDirectionsRoute(true)

        val result =
            MapRouteLine.MapRouteLineSupport.buildWayPointFeatureFromLeg(route.legs()!![0], 0)

        assertEquals("\"origin\"", result!!.properties()!!["wayPoint"].toString())
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionModerate() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.MODERATE_CONGESTION_VALUE, true)

        assertEquals(-27392, result)
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionHeavy() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.HEAVY_CONGESTION_VALUE, true)

        assertEquals(-45747, result)
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionSevere() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.SEVERE_CONGESTION_VALUE, true)

        assertEquals(-7396281, result)
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionUnknown() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.UNKNOWN_CONGESTION_VALUE, true)

        assertEquals(-11097861, result)
    }

    @Test
    fun getRouteColorForCongestionPrimaryRouteCongestionDefault() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result = mapRouteLine.getRouteColorForCongestion("foobar", true)

        assertEquals(-11097861, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionModerate() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.MODERATE_CONGESTION_VALUE, false)

        assertEquals(-4881791, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionHeavy() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.HEAVY_CONGESTION_VALUE, false)

        assertEquals(-4881791, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionSevere() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.SEVERE_CONGESTION_VALUE, false)

        assertEquals(-4881791, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionUnknown() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result =
            mapRouteLine.getRouteColorForCongestion(RouteConstants.UNKNOWN_CONGESTION_VALUE, false)

        assertEquals(-7957339, result)
    }

    @Test
    fun getRouteColorForCongestionNonPrimaryRouteCongestionDefault() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        val result = mapRouteLine.getRouteColorForCongestion("foobar", false)

        assertEquals(-7957339, result)
    }

    @Test
    fun reinitializeWithRoutes() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val route = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        mapRouteLine.reinitializeWithRoutes(listOf(route))

        assertEquals(route, mapRouteLine.getPrimaryRoute())
    }

    @Test
    fun reinitializePrimaryRoute() {
        every { style.layers } returns listOf(primaryRouteLayer)
        every { style.isFullyLoaded } returns true
        every { style.getLayer(PRIMARY_ROUTE_TRAFFIC_LAYER_ID) } returns primaryRouteLayer
        every { primaryRouteLayer.setFilter(any()) } returns Unit
        every { primaryRouteCasingLayer.setFilter(any()) } returns Unit
        every { alternativeRouteLayer.setFilter(any()) } returns Unit
        every { alternativeRouteCasingLayer.setFilter(any()) } returns Unit
        every { primaryRouteTrafficLayer.setFilter(any()) } returns Unit
        every { waypointLayer.setFilter(any()) } returns Unit
        every { primaryRouteLayer.setProperties(any()) } returns Unit
        every { primaryRouteCasingLayer.setProperties(any()) } returns Unit
        every { alternativeRouteLayer.setProperties(any()) } returns Unit
        every { alternativeRouteCasingLayer.setProperties(any()) } returns Unit
        every { primaryRouteTrafficLayer.setProperties(any()) } returns Unit
        every { waypointLayer.setProperties(any()) } returns Unit
        every {
            style.getLayerAs<LineLayer>("mapbox-navigation-route-casing-layer")
        } returns primaryRouteCasingLayer

        val route = getDirectionsRoute(true)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        )

        mapRouteLine.reinitializeWithRoutes(listOf(route))
        mapRouteLine.reinitializePrimaryRoute()

        verify { primaryRouteLayer.setProperties(any()) }
    }

    @Test
    fun getExpressionAtOffsetWhenExpressionDataEmpty() {
        every { style.layers } returns listOf(primaryRouteLayer)
        val expectedExpression = "[\"step\", [\"line-progress\"], [\"rgba\", 0.0, 0.0, 0.0, 0.0]," +
            " 0.2, [\"rgba\", 86.0, 168.0, 251.0, 1.0]]"
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            listOf<RouteFeatureData>(),
            listOf<RouteLineExpressionData>(),
            true,
            false,
            mapRouteSourceProvider,
            0f,
            null
        )

        val expression = mapRouteLine.getExpressionAtOffset(.2f)

        assertEquals(expectedExpression, expression.toString())
    }

    @Test
    fun updateVanishingPoint() {
        val expectedRouteLineVanishingExpression = "[\"step\", [\"line-progress\"], " +
            "[\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.12680313, [\"rgba\", 86.0, 168.0, 251.0, 1.0]]"
        val expectedRouteLineCasingVanishingExpression = "[\"step\", [\"line-progress\"], " +
            "[\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.12680313, [\"rgba\", 47.0, 122.0, 198.0, 1.0]]"
        val expectedRouteTrafficLineVanishingExpression = "[\"step\", [\"line-progress\"], " +
            "[\"rgba\", 0.0, 0.0, 0.0, 0.0], 0.12680313, [\"rgba\", 86.0, 168.0, 251.0, 1.0], " +
            "0.150941, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.1905395, [\"rgba\", 86.0, 168.0, " +
            "251.0, 1.0], 0.26964808, [\"rgba\", 255.0, 77.0, 77.0, 1.0], 0.27944908, " +
            "[\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.33272266, [\"rgba\", 86.0, 168.0, 251.0, " +
            "1.0], 0.39298487, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.48991048, [\"rgba\", " +
            "255.0, 77.0, 77.0, 1.0], 0.504132, [\"rgba\", 86.0, 168.0, 251.0, 1.0], 0.8017851, " +
            "[\"rgba\", 86.0, 168.0, 251.0, 1.0], 1.0000086, [\"rgba\", 86.0, 168.0, 251.0, 1.0]]"
        every { style.layers } returns listOf(primaryRouteLayer)
        every { style.isFullyLoaded } returnsMany listOf(
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            true,
            true,
            true
        )
        every {
            style.getLayerAs<LineLayer>("mapbox-navigation-route-casing-layer")
        } returns primaryRouteCasingLayer
        every { style.getLayer("mapbox-navigation-route-layer") } returns primaryRouteLayer
        every {
            style.getLayer("mapbox-navigation-route-traffic-layer")
        } returns primaryRouteTrafficLayer
        val routeLineExpressionSlot = slot<PropertyValue<Expression>>()
        val routeLineCasingExpressionSlot = slot<PropertyValue<Expression>>()
        val routeLineTrafficExpressionSlot = slot<PropertyValue<Expression>>()
        val route = getDirectionsRoute()
        val coordinates = LineString.fromPolyline(
            route.geometry()!!,
            Constants.PRECISION_6
        ).coordinates()
        val inputPoint = TurfMeasurement.midpoint(coordinates[4], coordinates[5])
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }
        mapRouteLine.inhibitVanishingPointUpdate(false)

        mapRouteLine.updateTraveledRouteLine(inputPoint)

        verify { primaryRouteCasingLayer.setProperties(capture(routeLineCasingExpressionSlot)) }
        verify { primaryRouteLayer.setProperties(capture(routeLineExpressionSlot)) }
        verify { primaryRouteTrafficLayer.setProperties(capture(routeLineTrafficExpressionSlot)) }
        assertEquals(
            expectedRouteLineVanishingExpression,
            routeLineExpressionSlot.captured.expression.toString()
        )
        assertEquals(
            expectedRouteLineCasingVanishingExpression,
            routeLineCasingExpressionSlot.captured.expression.toString()
        )

        assertEquals(
            expectedRouteTrafficLineVanishingExpression,
            routeLineTrafficExpressionSlot.captured.expression.toString()
        )
    }

    @Test
    fun updateVanishingPointInhibitedByDefault() {
        every { style.layers } returns listOf(primaryRouteLayer)
        every { style.isFullyLoaded } returnsMany listOf(
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            true,
            true,
            true
        )
        every {
            style.getLayerAs<LineLayer>("mapbox-navigation-route-casing-layer")
        } returns primaryRouteCasingLayer
        every { style.getLayer("mapbox-navigation-route-layer") } returns primaryRouteLayer
        every {
            style.getLayer("mapbox-navigation-route-traffic-layer")
        } returns primaryRouteTrafficLayer
        val route = getDirectionsRoute()
        val coordinates = LineString.fromPolyline(
            route.geometry()!!,
            Constants.PRECISION_6
        ).coordinates()
        val inputPoint = TurfMeasurement.midpoint(coordinates[4], coordinates[5])
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        mapRouteLine.updateTraveledRouteLine(inputPoint)

        verify(exactly = 0) { primaryRouteCasingLayer.setProperties(any()) }
        verify(exactly = 0) { primaryRouteLayer.setProperties(any()) }
        verify(exactly = 0) { primaryRouteTrafficLayer.setProperties(any()) }
    }

    @Test
    fun updateVanishingPointWhenPointDistanceBeyondThreshold() {
        every { style.layers } returns listOf(primaryRouteLayer)
        every { style.isFullyLoaded } returnsMany listOf(
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            true,
            true,
            true
        )
        every {
            style.getLayerAs<LineLayer>("mapbox-navigation-route-casing-layer")
        } returns primaryRouteCasingLayer
        every { style.getLayer("mapbox-navigation-route-layer") } returns primaryRouteLayer
        every {
            style.getLayer("mapbox-navigation-route-traffic-layer")
        } returns primaryRouteTrafficLayer
        val route = getDirectionsRoute()
        val inputPoint = Point.fromLngLat(-122.508527, 37.974846)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        mapRouteLine.updateTraveledRouteLine(inputPoint)

        verify(exactly = 0) { primaryRouteCasingLayer.setProperties(any()) }
        verify(exactly = 0) { primaryRouteLayer.setProperties(any()) }
        verify(exactly = 0) { primaryRouteTrafficLayer.setProperties(any()) }
    }

    @Test
    fun updateVanishingPointWhenLineCoordinatesIsLessThanTwoPoints() {
        every { style.layers } returns listOf(primaryRouteLayer)
        every { style.isFullyLoaded } returnsMany listOf(
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            true,
            true,
            true
        )
        every {
            style.getLayerAs<LineLayer>("mapbox-navigation-route-casing-layer")
        } returns primaryRouteCasingLayer
        every { style.getLayer("mapbox-navigation-route-layer") } returns primaryRouteLayer
        every {
            style.getLayer("mapbox-navigation-route-traffic-layer")
        } returns primaryRouteTrafficLayer
        val route = getSingleCoordinateDirectionsRoute()

        val inputPoint = Point.fromLngLat(-122.508527, 37.974846)
        val mapRouteLine = MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            null
        ).also { it.draw(listOf(route)) }

        mapRouteLine.updateTraveledRouteLine(inputPoint)

        verify(exactly = 0) { primaryRouteCasingLayer.setProperties(any()) }
        verify(exactly = 0) { primaryRouteLayer.setProperties(any()) }
        verify(exactly = 0) { primaryRouteTrafficLayer.setProperties(any()) }
    }

    private fun getDirectionsRoute(includeCongestion: Boolean): DirectionsRoute {
        val congestionValue = when (includeCongestion) {
            true -> "\"unknown\",\"heavy\",\"low\""
            false -> ""
        }
        val tokenHere = "someToken"
        val directionsRouteAsJson = loadJsonFixture("222.txt")
            ?.replace("tokenHere", tokenHere)
            ?.replace("congestion_value", congestionValue)

        return DirectionsRoute.fromJson(directionsRouteAsJson)
    }

    private fun getDirectionsRoute(): DirectionsRoute {
        val tokenHere = "someToken"
        val directionsRouteAsJson = loadJsonFixture("vanish_point_test.txt")
            ?.replace("tokenHere", tokenHere)

        return DirectionsRoute.fromJson(directionsRouteAsJson)
    }

    private fun getSingleCoordinateDirectionsRoute(): DirectionsRoute {
        val tokenHere = "someToken"
        val directionsRouteAsJson = loadJsonFixture("single_coordinate_route.json")
            ?.replace("tokenHere", tokenHere)

        return DirectionsRoute.fromJson(directionsRouteAsJson)
    }

    @Test
    fun onInitializedCallback() {
        val callback = mockk<MapRouteLineInitializedCallback>(relaxUnitFun = true)

        every { style.layers } returns listOf(primaryRouteLayer)
        MapRouteLine(
            ctx,
            style,
            styleRes,
            null,
            layerProvider,
            mapRouteSourceProvider,
            callback
        )

        verify {
            callback.onInitialized(
                RouteLineLayerIds(
                    PRIMARY_ROUTE_TRAFFIC_LAYER_ID,
                    PRIMARY_ROUTE_LAYER_ID,
                    listOf(ALTERNATIVE_ROUTE_LAYER_ID)
                )
            )
        }
    }

    @Test
    fun calculatePreciseDistanceTraveledAlongLineTest() {
        val route = getDirectionsRoute()
        val lineString = LineString.fromPolyline(
            route.geometry()!!,
            Constants.PRECISION_6
        )
        val targetPoint = TurfMeasurement.midpoint(
            lineString.coordinates()[6],
            lineString.coordinates()[7]
        )
        val subCoordinates = lineString.coordinates().subList(7, lineString.coordinates().size)
        val length = TurfMeasurement.length(
            listOf(targetPoint).plus(subCoordinates),
            TurfConstants.UNIT_METERS
        )

        val result = calculatePreciseDistanceTraveledAlongLine(
            lineString,
            length.toFloat() - 50,
            targetPoint
        )

        assertEquals(length, result, 0.01)
    }

    @Test
    fun calculatePreciseDistanceTraveledAlongLineWhenTargetFirstPoint() {
        val route = getDirectionsRoute()
        val lineString = LineString.fromPolyline(
            route.geometry()!!,
            Constants.PRECISION_6
        )
        val targetPoint = lineString.coordinates().first()
        val length = TurfMeasurement.length(lineString.coordinates(), TurfConstants.UNIT_METERS)

        val result = calculatePreciseDistanceTraveledAlongLine(
            lineString,
            length.toFloat() - 50,
            targetPoint
        )

        assertEquals(length, result, 0.01)
    }

    @Test
    fun calculatePreciseDistanceTraveledAlongLineWhenTargetLastPoint() {
        val route = getDirectionsRoute()
        val lineString = LineString.fromPolyline(
            route.geometry()!!,
            Constants.PRECISION_6
        )
        val lineCoordinates = lineString.coordinates()

        val targetPoint = TurfMeasurement.midpoint(
            lineCoordinates[lineCoordinates.lastIndex - 1],
            lineCoordinates[lineCoordinates.lastIndex]
        )
        val expectedLength = TurfMeasurement.distance(
            lineCoordinates.last(),
            targetPoint,
            TurfConstants.UNIT_METERS
        )

        val result = calculatePreciseDistanceTraveledAlongLine(
            lineString,
            0f,
            targetPoint
        )

        assertEquals(expectedLength, result, 0.01)
    }

    @Test
    fun getStyledFloatArrayTest() {
        val result = MapRouteLine.MapRouteLineSupport.getStyledFloatArray(
            R.styleable.MapboxStyleNavigationMapRoute_routeLineScaleStops,
            ctx,
            styleRes,
            R.styleable.MapboxStyleNavigationMapRoute
        )

        assertEquals(6, result.size)
        assertEquals(4.0f, result[0])
        assertEquals(10.0f, result[1])
        assertEquals(13.0f, result[2])
        assertEquals(16.0f, result[3])
        assertEquals(19.0f, result[4])
        assertEquals(22.0f, result[5])
    }

    @Test
    fun getStyledFloatArrayWhenResourceNotFount() {
        val result = MapRouteLine.MapRouteLineSupport.getStyledFloatArray(
            0,
            ctx,
            styleRes,
            R.styleable.MapboxStyleNavigationMapRoute
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun getRouteLineScalingValuesTest() {
        val result = MapRouteLine.MapRouteLineSupport.getRouteLineScalingValues(
            styleRes,
            ctx,
            R.styleable.MapboxStyleNavigationMapRoute_routeLineScaleStops,
            R.styleable.MapboxStyleNavigationMapRoute_routeLineScaleMultipliers,
            R.styleable.MapboxStyleNavigationMapRoute_routeLineScales,
            R.styleable.MapboxStyleNavigationMapRoute
        )

        assertEquals(result.size, 6)
        assertEquals(4.0f, result[0].scaleStop)
        assertEquals(3.0f, result[0].scaleMultiplier)
        assertEquals(1.0f, result[0].scale)
    }

    //todo put the test expression in a resource file
    @Test
    fun getRouteLineTrafficExpressionDataWhenStreetClassDataExists() {
        val routeAsJsonJson = "{\"routeIndex\":\"0\",\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"geometry\":\"mtylgAd`guhFzJxBl@aH`Ca\\\\je@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@bBgc@\\\\aGNyCLcFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@bLbGdE\\\\|JzAvIl@`WzA~Hl@nIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]{GuFwCrG\",\"weight\":451.469,\"weight_name\":\"auto\",\"legs\":[{\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"summary\":\"Lincoln Avenue, Francisco Boulevard West\",\"admins\":[{\"iso_3166_1\":\"US\",\"iso_3166_1_alpha3\":\"USA\"}],\"steps\":[{\"distance\":22.292,\"duration\":13.375,\"duration_typical\":13.375,\"geometry\":\"mtylgAd`guhFzJxB\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523666,37.975384],\"bearing_before\":0,\"bearing_after\":194,\"instruction\":\"Drive south.\",\"type\":\"depart\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.292,\"announcement\":\"Drive south. Then Turn left onto Laurel Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Drive south. Then Turn left onto Laurel Place.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":22.292,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":15.047,\"intersections\":[{\"location\":[-122.523666,37.975384],\"bearings\":[194],\"entry\":[true],\"out\":0,\"geometry_index\":0,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":54,\"duration\":27.103,\"duration_typical\":27.103,\"geometry\":\"qhylgA~cguhFl@aH`Ca\\\\\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523727,37.975193],\"bearing_before\":194,\"bearing_after\":101,\"instruction\":\"Turn left onto Laurel Place.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":44,\"announcement\":\"Turn right onto Lincoln Avenue.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Lincoln Avenue.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":54,\"primary\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":34.366,\"intersections\":[{\"location\":[-122.523727,37.975193],\"bearings\":[14,101],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":1,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.523582,37.97517],\"bearings\":[100,281],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":2,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":496,\"duration\":172.634,\"duration_typical\":172.634,\"geometry\":\"acylgAz}euhFje@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523117,37.975105],\"bearing_before\":100,\"bearing_after\":195,\"instruction\":\"Turn right onto Lincoln Avenue.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":480,\"announcement\":\"In a quarter mile, Turn left onto 2nd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In a quarter mile, Turn left onto 2nd Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":101.333,\"announcement\":\"Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":496,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":196.223,\"intersections\":[{\"location\":[-122.523117,37.975105],\"bearings\":[195,280],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":3,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523392,37.974293],\"bearings\":[13,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":5,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523453,37.974087],\"bearings\":[13,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":6,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523499,37.973911],\"bearings\":[12,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":8,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523643,37.973412],\"bearings\":[13,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":9,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523727,37.973034],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":10,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523804,37.972744],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":11,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523827,37.972656],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":12,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523849,37.972569],\"bearings\":[12,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":13,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523933,37.972202],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":14,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524055,37.971745],\"bearings\":[12,189],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":15,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524071,37.971672],\"bearings\":[9,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":16,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524094,37.971588],\"bearings\":[12,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":17,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524231,37.971043],\"bearings\":[11,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":18,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524269,37.970879],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":19,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}}]},{\"distance\":80,\"duration\":23.598,\"duration_typical\":23.598,\"geometry\":\"wsplgAvghuhFbBgc@\\\\aGNyCLcF\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.5243,37.970764],\"bearing_before\":192,\"bearing_after\":96,\"instruction\":\"Turn left onto 2nd Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":71.111,\"announcement\":\"Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":80,\"primary\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":29.888,\"intersections\":[{\"location\":[-122.5243,37.970764],\"bearings\":[12,96],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":20,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52372,37.970715],\"bearings\":[98,276],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":21,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52359,37.970699],\"bearings\":[97,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":22,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.523514,37.970692],\"bearings\":[95,277],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":23,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}}]},{\"distance\":286,\"duration\":33.487,\"duration_typical\":33.487,\"geometry\":\"wnplgAnofuhFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@\",\"name\":\"Francisco Boulevard West\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523399,37.970684],\"bearing_before\":95,\"bearing_after\":163,\"instruction\":\"Turn right onto Francisco Boulevard West.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":272.667,\"announcement\":\"In 900 feet, Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 900 feet, Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":66.667,\"announcement\":\"Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":286,\"primary\":{\"text\":\"Irwin Street\",\"components\":[{\"text\":\"Irwin Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":42.61,\"intersections\":[{\"location\":[-122.523399,37.970684],\"bearings\":[163,275],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":24,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523331,37.970501],\"bearings\":[137,343],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":26,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523193,37.970387],\"bearings\":[132,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":28,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523064,37.970295],\"bearings\":[126,312],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":30,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.522903,37.970203],\"bearings\":[140,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":32,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}}]},{\"distance\":139,\"duration\":34.933,\"duration_typical\":34.933,\"geometry\":\"kpllgAzubuhFbLbGdE\\\\|JzAvIl@`WzA~Hl@\",\"name\":\"Irwin Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521454,37.968662],\"bearing_before\":142,\"bearing_after\":206,\"instruction\":\"Turn right onto Irwin Street.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":129,\"announcement\":\"In 500 feet, Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 500 feet, Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":53.333,\"announcement\":\"Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":139,\"primary\":{\"text\":\"Du Bois Street\",\"components\":[{\"text\":\"Du Bois Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"slight left\"}}],\"driving_side\":\"right\",\"weight\":43.71,\"intersections\":[{\"location\":[-122.521454,37.968662],\"bearings\":[206,322],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":37,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521584,37.968452],\"bearings\":[26,187],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":38,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521599,37.968353],\"bearings\":[7,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":39,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521645,37.968163],\"bearings\":[11,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":40,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521667,37.967991],\"bearings\":[6,185],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":41,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":224,\"duration\":41.801,\"duration_typical\":41.801,\"geometry\":\"idjlgApgcuhFnIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]\",\"name\":\"Du Bois Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521736,37.967445],\"bearing_before\":188,\"bearing_after\":156,\"instruction\":\"Bear left onto Du Bois Street.\",\"type\":\"turn\",\"modifier\":\"slight left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":214,\"announcement\":\"In 700 feet, Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 700 feet, Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":45.833,\"announcement\":\"Turn left. Then Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":224,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":51.696,\"intersections\":[{\"location\":[-122.521736,37.967445],\"bearings\":[8,156],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":43,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521492,37.967026],\"bearings\":[147,334],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":45,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521355,37.966862],\"bearings\":[137,327],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":46,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521309,37.966824],\"bearings\":[140,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":47,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521248,37.966766],\"bearings\":[134,320],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":48,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.520851,37.96653],\"bearings\":[126,298],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":50,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.520485,37.966324],\"bearings\":[127,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":51,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":19,\"duration\":13.42,\"duration_typical\":13.42,\"geometry\":\"alglgAjz_uhF{GuF\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519989,37.966034],\"bearing_before\":127,\"bearing_after\":34,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":19,\"announcement\":\"Turn left. Then Your destination will be on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Your destination will be on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":19,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":19.018,\"intersections\":[{\"location\":[-122.519989,37.966034],\"bearings\":[34,307],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":52,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":14.744,\"duration\":17.583,\"duration_typical\":17.583,\"geometry\":\"}tglgAtr_uhFwCrG\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519867,37.966175],\"bearing_before\":34,\"bearing_after\":305,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":14.744,\"announcement\":\"Your destination is on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Your destination is on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":14.744,\"primary\":{\"text\":\"Your destination is on the left\",\"components\":[{\"text\":\"Your destination is on the left\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":18.91,\"intersections\":[{\"location\":[-122.519867,37.966175],\"bearings\":[214,305],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":53,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":0,\"duration\":0,\"duration_typical\":0,\"geometry\":\"uyglgAh{_uhF??\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.520004,37.966251],\"bearing_before\":305,\"bearing_after\":0,\"instruction\":\"Your destination is on the left.\",\"type\":\"arrive\",\"modifier\":\"left\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0,\"intersections\":[{\"location\":[-122.520004,37.966251],\"bearings\":[125],\"entry\":[true],\"in\":0,\"geometry_index\":54,\"admin_index\":0}]}],\"annotation\":{\"distance\":[21.9,13,41.5,70.9,22.7,23.5,6.5,13.4,57.1,42.7,33,10,10,41.4,52.1,8.2,9.6,61.9,18.6,13,51.2,11.5,6.7,10.1,11.1,10.5,8.3,9.2,7.7,7.6,11.3,6,7.7,11.2,21.5,31.6,141.8,26,11.1,21.6,19.2,43.1,17.9,20.3,31.1,21.9,5.8,8.3,26.4,17.6,39.5,54.2,19,14.7],\"congestion\":[\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\"]}}],\"routeOptions\":{\"baseUrl\":\"https:\\/\\/api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237328,37.9753972],[-122.5200609,37.9661962]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"token-here\",\"uuid\":\"4EDxslIYgRqso6fVkA3NC124fG5-200qq3ohWVmcRzqNo4-otP61qw==\"},\"voiceLocale\":\"en-US\"}"
        val route =  DirectionsRoute.fromJson(routeAsJsonJson)

        val result = getRouteLineTrafficExpressionData(route.legs()!![0])

        assertEquals(41, result.size)
        assertEquals(1319.0000000000002, result.last().distanceFromOrigin, 0.0)
        assertEquals(RouteConstants.LOW_CONGESTION_VALUE, result.last().trafficCongestionIdentifier)
        assertEquals("service", result.last().roadClass)
    }

    //todo put the test expression in a resource file
    @Test
    fun getRouteLineTrafficExpressionDataWhenStreetClassDataDoesNotExist() {
        val routeAsJsonJson = "{\"routeIndex\":\"0\",\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"geometry\":\"mtylgAd`guhFzJxBl@aH`Ca\\\\je@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@bBgc@\\\\aGNyCLcFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@bLbGdE\\\\|JzAvIl@`WzA~Hl@nIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]{GuFwCrG\",\"weight\":451.469,\"weight_name\":\"auto\",\"legs\":[{\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"summary\":\"Lincoln Avenue, Francisco Boulevard West\",\"admins\":[{\"iso_3166_1\":\"US\",\"iso_3166_1_alpha3\":\"USA\"}],\"steps\":[{\"distance\":22.292,\"duration\":13.375,\"duration_typical\":13.375,\"geometry\":\"mtylgAd`guhFzJxB\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523666,37.975384],\"bearing_before\":0,\"bearing_after\":194,\"instruction\":\"Drive south.\",\"type\":\"depart\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.292,\"announcement\":\"Drive south. Then Turn left onto Laurel Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Drive south. Then Turn left onto Laurel Place.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":22.292,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":15.047,\"intersections\":[{\"location\":[-122.523666,37.975384],\"bearings\":[194],\"entry\":[true],\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":54,\"duration\":27.103,\"duration_typical\":27.103,\"geometry\":\"qhylgA~cguhFl@aH`Ca\\\\\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523727,37.975193],\"bearing_before\":194,\"bearing_after\":101,\"instruction\":\"Turn left onto Laurel Place.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":44,\"announcement\":\"Turn right onto Lincoln Avenue.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Lincoln Avenue.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":54,\"primary\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":34.366,\"intersections\":[{\"location\":[-122.523727,37.975193],\"bearings\":[14,101],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523582,37.97517],\"bearings\":[100,281],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":496,\"duration\":172.634,\"duration_typical\":172.634,\"geometry\":\"acylgAz}euhFje@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523117,37.975105],\"bearing_before\":100,\"bearing_after\":195,\"instruction\":\"Turn right onto Lincoln Avenue.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":480,\"announcement\":\"In a quarter mile, Turn left onto 2nd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In a quarter mile, Turn left onto 2nd Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":101.333,\"announcement\":\"Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":496,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":196.223,\"intersections\":[{\"location\":[-122.523117,37.975105],\"bearings\":[195,280],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523392,37.974293],\"bearings\":[13,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523453,37.974087],\"bearings\":[13,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523499,37.973911],\"bearings\":[12,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523643,37.973412],\"bearings\":[13,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523727,37.973034],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523804,37.972744],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523827,37.972656],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523849,37.972569],\"bearings\":[12,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523933,37.972202],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524055,37.971745],\"bearings\":[12,189],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524071,37.971672],\"bearings\":[9,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524094,37.971588],\"bearings\":[12,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524231,37.971043],\"bearings\":[11,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524269,37.970879],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":80,\"duration\":23.598,\"duration_typical\":23.598,\"geometry\":\"wsplgAvghuhFbBgc@\\\\aGNyCLcF\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.5243,37.970764],\"bearing_before\":192,\"bearing_after\":96,\"instruction\":\"Turn left onto 2nd Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":71.111,\"announcement\":\"Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":80,\"primary\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":29.888,\"intersections\":[{\"location\":[-122.5243,37.970764],\"bearings\":[12,96],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.52372,37.970715],\"bearings\":[98,276],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.52359,37.970699],\"bearings\":[97,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523514,37.970692],\"bearings\":[95,277],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":286,\"duration\":33.487,\"duration_typical\":33.487,\"geometry\":\"wnplgAnofuhFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@\",\"name\":\"Francisco Boulevard West\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523399,37.970684],\"bearing_before\":95,\"bearing_after\":163,\"instruction\":\"Turn right onto Francisco Boulevard West.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":272.667,\"announcement\":\"In 900 feet, Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 900 feet, Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":66.667,\"announcement\":\"Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":286,\"primary\":{\"text\":\"Irwin Street\",\"components\":[{\"text\":\"Irwin Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":42.61,\"intersections\":[{\"location\":[-122.523399,37.970684],\"bearings\":[163,275],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523331,37.970501],\"bearings\":[137,343],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523193,37.970387],\"bearings\":[132,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523064,37.970295],\"bearings\":[126,312],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.522903,37.970203],\"bearings\":[140,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":139,\"duration\":34.933,\"duration_typical\":34.933,\"geometry\":\"kpllgAzubuhFbLbGdE\\\\|JzAvIl@`WzA~Hl@\",\"name\":\"Irwin Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521454,37.968662],\"bearing_before\":142,\"bearing_after\":206,\"instruction\":\"Turn right onto Irwin Street.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":129,\"announcement\":\"In 500 feet, Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 500 feet, Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":53.333,\"announcement\":\"Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":139,\"primary\":{\"text\":\"Du Bois Street\",\"components\":[{\"text\":\"Du Bois Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"slight left\"}}],\"driving_side\":\"right\",\"weight\":43.71,\"intersections\":[{\"location\":[-122.521454,37.968662],\"bearings\":[206,322],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521584,37.968452],\"bearings\":[26,187],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521599,37.968353],\"bearings\":[7,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521645,37.968163],\"bearings\":[11,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521667,37.967991],\"bearings\":[6,185],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":224,\"duration\":41.801,\"duration_typical\":41.801,\"geometry\":\"idjlgApgcuhFnIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]\",\"name\":\"Du Bois Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521736,37.967445],\"bearing_before\":188,\"bearing_after\":156,\"instruction\":\"Bear left onto Du Bois Street.\",\"type\":\"turn\",\"modifier\":\"slight left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":214,\"announcement\":\"In 700 feet, Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 700 feet, Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":45.833,\"announcement\":\"Turn left. Then Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":224,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":51.696,\"intersections\":[{\"location\":[-122.521736,37.967445],\"bearings\":[8,156],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521492,37.967026],\"bearings\":[147,334],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521355,37.966862],\"bearings\":[137,327],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521309,37.966824],\"bearings\":[140,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521248,37.966766],\"bearings\":[134,320],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.520851,37.96653],\"bearings\":[126,298],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.520485,37.966324],\"bearings\":[127,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":19,\"duration\":13.42,\"duration_typical\":13.42,\"geometry\":\"alglgAjz_uhF{GuF\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519989,37.966034],\"bearing_before\":127,\"bearing_after\":34,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":19,\"announcement\":\"Turn left. Then Your destination will be on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Your destination will be on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":19,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":19.018,\"intersections\":[{\"location\":[-122.519989,37.966034],\"bearings\":[34,307],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":14.744,\"duration\":17.583,\"duration_typical\":17.583,\"geometry\":\"}tglgAtr_uhFwCrG\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519867,37.966175],\"bearing_before\":34,\"bearing_after\":305,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":14.744,\"announcement\":\"Your destination is on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Your destination is on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":14.744,\"primary\":{\"text\":\"Your destination is on the left\",\"components\":[{\"text\":\"Your destination is on the left\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":18.91,\"intersections\":[{\"location\":[-122.519867,37.966175],\"bearings\":[214,305],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":0,\"duration\":0,\"duration_typical\":0,\"geometry\":\"uyglgAh{_uhF??\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.520004,37.966251],\"bearing_before\":305,\"bearing_after\":0,\"instruction\":\"Your destination is on the left.\",\"type\":\"arrive\",\"modifier\":\"left\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0,\"intersections\":[{\"location\":[-122.520004,37.966251],\"bearings\":[125],\"entry\":[true],\"in\":0,\"admin_index\":0}]}],\"annotation\":{\"distance\":[21.9,13,41.5,70.9,22.7,23.5,6.5,13.4,57.1,42.7,33,10,10,41.4,52.1,8.2,9.6,61.9,18.6,13,51.2,11.5,6.7,10.1,11.1,10.5,8.3,9.2,7.7,7.6,11.3,6,7.7,11.2,21.5,31.6,141.8,26,11.1,21.6,19.2,43.1,17.9,20.3,31.1,21.9,5.8,8.3,26.4,17.6,39.5,54.2,19,14.7],\"congestion\":[\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\"]}}],\"routeOptions\":{\"baseUrl\":\"https:\\/\\/api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237328,37.9753972],[-122.5200609,37.9661962]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"token-here\",\"uuid\":\"4EDxslIYgRqso6fVkA3NC124fG5-200qq3ohWVmcRzqNo4-otP61qw==\"},\"voiceLocale\":\"en-US\"}"
        val route =  DirectionsRoute.fromJson(routeAsJsonJson)

        val result = getRouteLineTrafficExpressionData(route.legs()!![0])

        assertEquals(54, result.size)
        assertEquals(1319.0000000000002, result.last().distanceFromOrigin, 0.0)
        assertEquals(RouteConstants.LOW_CONGESTION_VALUE, result.last().trafficCongestionIdentifier)
        assertNull(result.last().roadClass)
    }

    @Test
    fun getRouteLineExpressionDataWithStreetClassOverrideWhenHasStreetClasses() {
        val routeAsJsonJson = "{\"routeIndex\":\"0\",\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"geometry\":\"mtylgAd`guhFzJxBl@aH`Ca\\\\je@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@bBgc@\\\\aGNyCLcFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@bLbGdE\\\\|JzAvIl@`WzA~Hl@nIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]{GuFwCrG\",\"weight\":451.469,\"weight_name\":\"auto\",\"legs\":[{\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"summary\":\"Lincoln Avenue, Francisco Boulevard West\",\"admins\":[{\"iso_3166_1\":\"US\",\"iso_3166_1_alpha3\":\"USA\"}],\"steps\":[{\"distance\":22.292,\"duration\":13.375,\"duration_typical\":13.375,\"geometry\":\"mtylgAd`guhFzJxB\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523666,37.975384],\"bearing_before\":0,\"bearing_after\":194,\"instruction\":\"Drive south.\",\"type\":\"depart\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.292,\"announcement\":\"Drive south. Then Turn left onto Laurel Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Drive south. Then Turn left onto Laurel Place.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":22.292,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":15.047,\"intersections\":[{\"location\":[-122.523666,37.975384],\"bearings\":[194],\"entry\":[true],\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":54,\"duration\":27.103,\"duration_typical\":27.103,\"geometry\":\"qhylgA~cguhFl@aH`Ca\\\\\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523727,37.975193],\"bearing_before\":194,\"bearing_after\":101,\"instruction\":\"Turn left onto Laurel Place.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":44,\"announcement\":\"Turn right onto Lincoln Avenue.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Lincoln Avenue.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":54,\"primary\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":34.366,\"intersections\":[{\"location\":[-122.523727,37.975193],\"bearings\":[14,101],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523582,37.97517],\"bearings\":[100,281],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":496,\"duration\":172.634,\"duration_typical\":172.634,\"geometry\":\"acylgAz}euhFje@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523117,37.975105],\"bearing_before\":100,\"bearing_after\":195,\"instruction\":\"Turn right onto Lincoln Avenue.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":480,\"announcement\":\"In a quarter mile, Turn left onto 2nd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In a quarter mile, Turn left onto 2nd Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":101.333,\"announcement\":\"Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":496,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":196.223,\"intersections\":[{\"location\":[-122.523117,37.975105],\"bearings\":[195,280],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523392,37.974293],\"bearings\":[13,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523453,37.974087],\"bearings\":[13,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523499,37.973911],\"bearings\":[12,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523643,37.973412],\"bearings\":[13,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523727,37.973034],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523804,37.972744],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523827,37.972656],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523849,37.972569],\"bearings\":[12,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523933,37.972202],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524055,37.971745],\"bearings\":[12,189],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524071,37.971672],\"bearings\":[9,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524094,37.971588],\"bearings\":[12,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524231,37.971043],\"bearings\":[11,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524269,37.970879],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":80,\"duration\":23.598,\"duration_typical\":23.598,\"geometry\":\"wsplgAvghuhFbBgc@\\\\aGNyCLcF\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.5243,37.970764],\"bearing_before\":192,\"bearing_after\":96,\"instruction\":\"Turn left onto 2nd Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":71.111,\"announcement\":\"Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":80,\"primary\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":29.888,\"intersections\":[{\"location\":[-122.5243,37.970764],\"bearings\":[12,96],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.52372,37.970715],\"bearings\":[98,276],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.52359,37.970699],\"bearings\":[97,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523514,37.970692],\"bearings\":[95,277],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":286,\"duration\":33.487,\"duration_typical\":33.487,\"geometry\":\"wnplgAnofuhFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@\",\"name\":\"Francisco Boulevard West\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523399,37.970684],\"bearing_before\":95,\"bearing_after\":163,\"instruction\":\"Turn right onto Francisco Boulevard West.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":272.667,\"announcement\":\"In 900 feet, Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 900 feet, Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":66.667,\"announcement\":\"Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":286,\"primary\":{\"text\":\"Irwin Street\",\"components\":[{\"text\":\"Irwin Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":42.61,\"intersections\":[{\"location\":[-122.523399,37.970684],\"bearings\":[163,275],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523331,37.970501],\"bearings\":[137,343],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523193,37.970387],\"bearings\":[132,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523064,37.970295],\"bearings\":[126,312],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.522903,37.970203],\"bearings\":[140,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":139,\"duration\":34.933,\"duration_typical\":34.933,\"geometry\":\"kpllgAzubuhFbLbGdE\\\\|JzAvIl@`WzA~Hl@\",\"name\":\"Irwin Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521454,37.968662],\"bearing_before\":142,\"bearing_after\":206,\"instruction\":\"Turn right onto Irwin Street.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":129,\"announcement\":\"In 500 feet, Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 500 feet, Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":53.333,\"announcement\":\"Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":139,\"primary\":{\"text\":\"Du Bois Street\",\"components\":[{\"text\":\"Du Bois Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"slight left\"}}],\"driving_side\":\"right\",\"weight\":43.71,\"intersections\":[{\"location\":[-122.521454,37.968662],\"bearings\":[206,322],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521584,37.968452],\"bearings\":[26,187],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521599,37.968353],\"bearings\":[7,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521645,37.968163],\"bearings\":[11,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521667,37.967991],\"bearings\":[6,185],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":224,\"duration\":41.801,\"duration_typical\":41.801,\"geometry\":\"idjlgApgcuhFnIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]\",\"name\":\"Du Bois Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521736,37.967445],\"bearing_before\":188,\"bearing_after\":156,\"instruction\":\"Bear left onto Du Bois Street.\",\"type\":\"turn\",\"modifier\":\"slight left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":214,\"announcement\":\"In 700 feet, Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 700 feet, Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":45.833,\"announcement\":\"Turn left. Then Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":224,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":51.696,\"intersections\":[{\"location\":[-122.521736,37.967445],\"bearings\":[8,156],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521492,37.967026],\"bearings\":[147,334],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521355,37.966862],\"bearings\":[137,327],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521309,37.966824],\"bearings\":[140,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521248,37.966766],\"bearings\":[134,320],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.520851,37.96653],\"bearings\":[126,298],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.520485,37.966324],\"bearings\":[127,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":19,\"duration\":13.42,\"duration_typical\":13.42,\"geometry\":\"alglgAjz_uhF{GuF\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519989,37.966034],\"bearing_before\":127,\"bearing_after\":34,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":19,\"announcement\":\"Turn left. Then Your destination will be on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Your destination will be on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":19,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":19.018,\"intersections\":[{\"location\":[-122.519989,37.966034],\"bearings\":[34,307],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":14.744,\"duration\":17.583,\"duration_typical\":17.583,\"geometry\":\"}tglgAtr_uhFwCrG\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519867,37.966175],\"bearing_before\":34,\"bearing_after\":305,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":14.744,\"announcement\":\"Your destination is on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Your destination is on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":14.744,\"primary\":{\"text\":\"Your destination is on the left\",\"components\":[{\"text\":\"Your destination is on the left\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":18.91,\"intersections\":[{\"location\":[-122.519867,37.966175],\"bearings\":[214,305],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":0,\"duration\":0,\"duration_typical\":0,\"geometry\":\"uyglgAh{_uhF??\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.520004,37.966251],\"bearing_before\":305,\"bearing_after\":0,\"instruction\":\"Your destination is on the left.\",\"type\":\"arrive\",\"modifier\":\"left\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0,\"intersections\":[{\"location\":[-122.520004,37.966251],\"bearings\":[125],\"entry\":[true],\"in\":0,\"admin_index\":0}]}],\"annotation\":{\"distance\":[21.9,13,41.5,70.9,22.7,23.5,6.5,13.4,57.1,42.7,33,10,10,41.4,52.1,8.2,9.6,61.9,18.6,13,51.2,11.5,6.7,10.1,11.1,10.5,8.3,9.2,7.7,7.6,11.3,6,7.7,11.2,21.5,31.6,141.8,26,11.1,21.6,19.2,43.1,17.9,20.3,31.1,21.9,5.8,8.3,26.4,17.6,39.5,54.2,19,14.7],\"congestion\":[\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\"]}}],\"routeOptions\":{\"baseUrl\":\"https:\\/\\/api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237328,37.9753972],[-122.5200609,37.9661962]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"token-here\",\"uuid\":\"4EDxslIYgRqso6fVkA3NC124fG5-200qq3ohWVmcRzqNo4-otP61qw==\"},\"voiceLocale\":\"en-US\"}"
        val route =  DirectionsRoute.fromJson(routeAsJsonJson)
        val trafficExpressionData = getRouteLineTrafficExpressionData(route.legs()!![0])

        val result = getRouteLineExpressionDataWithStreetClassOverride(
            trafficExpressionData,
            route.distance(),
            { _, _, -> -9},
            true
        )

        assertEquals(trafficExpressionData.size, result.size)
        assertEquals(0.016404051f, result[1].offset)
        // todo assert on the segment color for an index that would get replaced.
    }

    @Test
    fun getRouteLineExpressionDataWithStreetClassOverrideWhenDoesNotHaveStreetClasses() {
        val routeAsJsonJson = "{\"routeIndex\":\"0\",\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"geometry\":\"mtylgAd`guhFzJxBl@aH`Ca\\\\je@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@bBgc@\\\\aGNyCLcFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@bLbGdE\\\\|JzAvIl@`WzA~Hl@nIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]{GuFwCrG\",\"weight\":451.469,\"weight_name\":\"auto\",\"legs\":[{\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"summary\":\"Lincoln Avenue, Francisco Boulevard West\",\"admins\":[{\"iso_3166_1\":\"US\",\"iso_3166_1_alpha3\":\"USA\"}],\"steps\":[{\"distance\":22.292,\"duration\":13.375,\"duration_typical\":13.375,\"geometry\":\"mtylgAd`guhFzJxB\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523666,37.975384],\"bearing_before\":0,\"bearing_after\":194,\"instruction\":\"Drive south.\",\"type\":\"depart\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.292,\"announcement\":\"Drive south. Then Turn left onto Laurel Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Drive south. Then Turn left onto Laurel Place.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":22.292,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":15.047,\"intersections\":[{\"location\":[-122.523666,37.975384],\"bearings\":[194],\"entry\":[true],\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":54,\"duration\":27.103,\"duration_typical\":27.103,\"geometry\":\"qhylgA~cguhFl@aH`Ca\\\\\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523727,37.975193],\"bearing_before\":194,\"bearing_after\":101,\"instruction\":\"Turn left onto Laurel Place.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":44,\"announcement\":\"Turn right onto Lincoln Avenue.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Lincoln Avenue.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":54,\"primary\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":34.366,\"intersections\":[{\"location\":[-122.523727,37.975193],\"bearings\":[14,101],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523582,37.97517],\"bearings\":[100,281],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":496,\"duration\":172.634,\"duration_typical\":172.634,\"geometry\":\"acylgAz}euhFje@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523117,37.975105],\"bearing_before\":100,\"bearing_after\":195,\"instruction\":\"Turn right onto Lincoln Avenue.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":480,\"announcement\":\"In a quarter mile, Turn left onto 2nd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In a quarter mile, Turn left onto 2nd Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":101.333,\"announcement\":\"Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":496,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":196.223,\"intersections\":[{\"location\":[-122.523117,37.975105],\"bearings\":[195,280],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523392,37.974293],\"bearings\":[13,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523453,37.974087],\"bearings\":[13,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523499,37.973911],\"bearings\":[12,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523643,37.973412],\"bearings\":[13,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523727,37.973034],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523804,37.972744],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523827,37.972656],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523849,37.972569],\"bearings\":[12,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523933,37.972202],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524055,37.971745],\"bearings\":[12,189],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524071,37.971672],\"bearings\":[9,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524094,37.971588],\"bearings\":[12,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524231,37.971043],\"bearings\":[11,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524269,37.970879],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":80,\"duration\":23.598,\"duration_typical\":23.598,\"geometry\":\"wsplgAvghuhFbBgc@\\\\aGNyCLcF\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.5243,37.970764],\"bearing_before\":192,\"bearing_after\":96,\"instruction\":\"Turn left onto 2nd Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":71.111,\"announcement\":\"Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":80,\"primary\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":29.888,\"intersections\":[{\"location\":[-122.5243,37.970764],\"bearings\":[12,96],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.52372,37.970715],\"bearings\":[98,276],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.52359,37.970699],\"bearings\":[97,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523514,37.970692],\"bearings\":[95,277],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":286,\"duration\":33.487,\"duration_typical\":33.487,\"geometry\":\"wnplgAnofuhFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@\",\"name\":\"Francisco Boulevard West\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523399,37.970684],\"bearing_before\":95,\"bearing_after\":163,\"instruction\":\"Turn right onto Francisco Boulevard West.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":272.667,\"announcement\":\"In 900 feet, Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 900 feet, Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":66.667,\"announcement\":\"Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":286,\"primary\":{\"text\":\"Irwin Street\",\"components\":[{\"text\":\"Irwin Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":42.61,\"intersections\":[{\"location\":[-122.523399,37.970684],\"bearings\":[163,275],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523331,37.970501],\"bearings\":[137,343],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523193,37.970387],\"bearings\":[132,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523064,37.970295],\"bearings\":[126,312],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.522903,37.970203],\"bearings\":[140,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":139,\"duration\":34.933,\"duration_typical\":34.933,\"geometry\":\"kpllgAzubuhFbLbGdE\\\\|JzAvIl@`WzA~Hl@\",\"name\":\"Irwin Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521454,37.968662],\"bearing_before\":142,\"bearing_after\":206,\"instruction\":\"Turn right onto Irwin Street.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":129,\"announcement\":\"In 500 feet, Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 500 feet, Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":53.333,\"announcement\":\"Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":139,\"primary\":{\"text\":\"Du Bois Street\",\"components\":[{\"text\":\"Du Bois Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"slight left\"}}],\"driving_side\":\"right\",\"weight\":43.71,\"intersections\":[{\"location\":[-122.521454,37.968662],\"bearings\":[206,322],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521584,37.968452],\"bearings\":[26,187],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521599,37.968353],\"bearings\":[7,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521645,37.968163],\"bearings\":[11,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521667,37.967991],\"bearings\":[6,185],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":224,\"duration\":41.801,\"duration_typical\":41.801,\"geometry\":\"idjlgApgcuhFnIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]\",\"name\":\"Du Bois Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521736,37.967445],\"bearing_before\":188,\"bearing_after\":156,\"instruction\":\"Bear left onto Du Bois Street.\",\"type\":\"turn\",\"modifier\":\"slight left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":214,\"announcement\":\"In 700 feet, Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 700 feet, Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":45.833,\"announcement\":\"Turn left. Then Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":224,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":51.696,\"intersections\":[{\"location\":[-122.521736,37.967445],\"bearings\":[8,156],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521492,37.967026],\"bearings\":[147,334],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521355,37.966862],\"bearings\":[137,327],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521309,37.966824],\"bearings\":[140,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521248,37.966766],\"bearings\":[134,320],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.520851,37.96653],\"bearings\":[126,298],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.520485,37.966324],\"bearings\":[127,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":19,\"duration\":13.42,\"duration_typical\":13.42,\"geometry\":\"alglgAjz_uhF{GuF\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519989,37.966034],\"bearing_before\":127,\"bearing_after\":34,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":19,\"announcement\":\"Turn left. Then Your destination will be on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Your destination will be on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":19,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":19.018,\"intersections\":[{\"location\":[-122.519989,37.966034],\"bearings\":[34,307],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":14.744,\"duration\":17.583,\"duration_typical\":17.583,\"geometry\":\"}tglgAtr_uhFwCrG\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519867,37.966175],\"bearing_before\":34,\"bearing_after\":305,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":14.744,\"announcement\":\"Your destination is on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Your destination is on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":14.744,\"primary\":{\"text\":\"Your destination is on the left\",\"components\":[{\"text\":\"Your destination is on the left\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":18.91,\"intersections\":[{\"location\":[-122.519867,37.966175],\"bearings\":[214,305],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":0,\"duration\":0,\"duration_typical\":0,\"geometry\":\"uyglgAh{_uhF??\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.520004,37.966251],\"bearing_before\":305,\"bearing_after\":0,\"instruction\":\"Your destination is on the left.\",\"type\":\"arrive\",\"modifier\":\"left\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0,\"intersections\":[{\"location\":[-122.520004,37.966251],\"bearings\":[125],\"entry\":[true],\"in\":0,\"admin_index\":0}]}],\"annotation\":{\"distance\":[21.9,13,41.5,70.9,22.7,23.5,6.5,13.4,57.1,42.7,33,10,10,41.4,52.1,8.2,9.6,61.9,18.6,13,51.2,11.5,6.7,10.1,11.1,10.5,8.3,9.2,7.7,7.6,11.3,6,7.7,11.2,21.5,31.6,141.8,26,11.1,21.6,19.2,43.1,17.9,20.3,31.1,21.9,5.8,8.3,26.4,17.6,39.5,54.2,19,14.7],\"congestion\":[\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\"]}}],\"routeOptions\":{\"baseUrl\":\"https:\\/\\/api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237328,37.9753972],[-122.5200609,37.9661962]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"token-here\",\"uuid\":\"4EDxslIYgRqso6fVkA3NC124fG5-200qq3ohWVmcRzqNo4-otP61qw==\"},\"voiceLocale\":\"en-US\"}"
        val route =  DirectionsRoute.fromJson(routeAsJsonJson)
        val trafficExpressionData = getRouteLineTrafficExpressionData(route.legs()!![0])

        val result = getRouteLineExpressionDataWithStreetClassOverride(
            trafficExpressionData,
            route.distance(),
            { _, _, -> -9},
            true
        )

        assertEquals(trafficExpressionData.size, result.size)
        assertEquals(0.016404051f, result[1].offset)
        assertEquals(-9, result[1].segmentColor)
    }

    // @Test
    // fun routeTrafficExpTest2() {
    //     val routeWithRoadClassesJson = "{\"routeIndex\":\"0\",\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"geometry\":\"mtylgAd`guhFzJxBl@aH`Ca\\\\je@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@bBgc@\\\\aGNyCLcFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@bLbGdE\\\\|JzAvIl@`WzA~Hl@nIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]{GuFwCrG\",\"weight\":451.469,\"weight_name\":\"auto\",\"legs\":[{\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"summary\":\"Lincoln Avenue, Francisco Boulevard West\",\"admins\":[{\"iso_3166_1\":\"US\",\"iso_3166_1_alpha3\":\"USA\"}],\"steps\":[{\"distance\":22.292,\"duration\":13.375,\"duration_typical\":13.375,\"geometry\":\"mtylgAd`guhFzJxB\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523666,37.975384],\"bearing_before\":0,\"bearing_after\":194,\"instruction\":\"Drive south.\",\"type\":\"depart\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.292,\"announcement\":\"Drive south. Then Turn left onto Laurel Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Drive south. Then Turn left onto Laurel Place.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":22.292,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":15.047,\"intersections\":[{\"location\":[-122.523666,37.975384],\"bearings\":[194],\"entry\":[true],\"out\":0,\"geometry_index\":0,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":54,\"duration\":27.103,\"duration_typical\":27.103,\"geometry\":\"qhylgA~cguhFl@aH`Ca\\\\\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523727,37.975193],\"bearing_before\":194,\"bearing_after\":101,\"instruction\":\"Turn left onto Laurel Place.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":44,\"announcement\":\"Turn right onto Lincoln Avenue.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Lincoln Avenue.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":54,\"primary\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":34.366,\"intersections\":[{\"location\":[-122.523727,37.975193],\"bearings\":[14,101],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":1,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.523582,37.97517],\"bearings\":[100,281],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":2,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":496,\"duration\":172.634,\"duration_typical\":172.634,\"geometry\":\"acylgAz}euhFje@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523117,37.975105],\"bearing_before\":100,\"bearing_after\":195,\"instruction\":\"Turn right onto Lincoln Avenue.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":480,\"announcement\":\"In a quarter mile, Turn left onto 2nd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In a quarter mile, Turn left onto 2nd Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":101.333,\"announcement\":\"Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":496,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":196.223,\"intersections\":[{\"location\":[-122.523117,37.975105],\"bearings\":[195,280],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":3,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523392,37.974293],\"bearings\":[13,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":5,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523453,37.974087],\"bearings\":[13,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":6,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523499,37.973911],\"bearings\":[12,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":8,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523643,37.973412],\"bearings\":[13,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":9,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523727,37.973034],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":10,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523804,37.972744],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":11,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523827,37.972656],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":12,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523849,37.972569],\"bearings\":[12,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":13,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523933,37.972202],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":14,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524055,37.971745],\"bearings\":[12,189],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":15,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524071,37.971672],\"bearings\":[9,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":16,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524094,37.971588],\"bearings\":[12,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":17,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524231,37.971043],\"bearings\":[11,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":18,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524269,37.970879],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":19,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}}]},{\"distance\":80,\"duration\":23.598,\"duration_typical\":23.598,\"geometry\":\"wsplgAvghuhFbBgc@\\\\aGNyCLcF\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.5243,37.970764],\"bearing_before\":192,\"bearing_after\":96,\"instruction\":\"Turn left onto 2nd Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":71.111,\"announcement\":\"Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":80,\"primary\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":29.888,\"intersections\":[{\"location\":[-122.5243,37.970764],\"bearings\":[12,96],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":20,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52372,37.970715],\"bearings\":[98,276],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":21,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52359,37.970699],\"bearings\":[97,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":22,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.523514,37.970692],\"bearings\":[95,277],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":23,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}}]},{\"distance\":286,\"duration\":33.487,\"duration_typical\":33.487,\"geometry\":\"wnplgAnofuhFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@\",\"name\":\"Francisco Boulevard West\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523399,37.970684],\"bearing_before\":95,\"bearing_after\":163,\"instruction\":\"Turn right onto Francisco Boulevard West.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":272.667,\"announcement\":\"In 900 feet, Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 900 feet, Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":66.667,\"announcement\":\"Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":286,\"primary\":{\"text\":\"Irwin Street\",\"components\":[{\"text\":\"Irwin Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":42.61,\"intersections\":[{\"location\":[-122.523399,37.970684],\"bearings\":[163,275],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":24,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523331,37.970501],\"bearings\":[137,343],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":26,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523193,37.970387],\"bearings\":[132,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":28,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523064,37.970295],\"bearings\":[126,312],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":30,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.522903,37.970203],\"bearings\":[140,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":32,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}}]},{\"distance\":139,\"duration\":34.933,\"duration_typical\":34.933,\"geometry\":\"kpllgAzubuhFbLbGdE\\\\|JzAvIl@`WzA~Hl@\",\"name\":\"Irwin Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521454,37.968662],\"bearing_before\":142,\"bearing_after\":206,\"instruction\":\"Turn right onto Irwin Street.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":129,\"announcement\":\"In 500 feet, Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 500 feet, Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":53.333,\"announcement\":\"Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":139,\"primary\":{\"text\":\"Du Bois Street\",\"components\":[{\"text\":\"Du Bois Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"slight left\"}}],\"driving_side\":\"right\",\"weight\":43.71,\"intersections\":[{\"location\":[-122.521454,37.968662],\"bearings\":[206,322],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":37,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521584,37.968452],\"bearings\":[26,187],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":38,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521599,37.968353],\"bearings\":[7,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":39,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521645,37.968163],\"bearings\":[11,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":40,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521667,37.967991],\"bearings\":[6,185],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":41,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":224,\"duration\":41.801,\"duration_typical\":41.801,\"geometry\":\"idjlgApgcuhFnIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]\",\"name\":\"Du Bois Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521736,37.967445],\"bearing_before\":188,\"bearing_after\":156,\"instruction\":\"Bear left onto Du Bois Street.\",\"type\":\"turn\",\"modifier\":\"slight left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":214,\"announcement\":\"In 700 feet, Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 700 feet, Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":45.833,\"announcement\":\"Turn left. Then Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":224,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":51.696,\"intersections\":[{\"location\":[-122.521736,37.967445],\"bearings\":[8,156],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":43,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521492,37.967026],\"bearings\":[147,334],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":45,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521355,37.966862],\"bearings\":[137,327],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":46,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521309,37.966824],\"bearings\":[140,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":47,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521248,37.966766],\"bearings\":[134,320],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":48,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.520851,37.96653],\"bearings\":[126,298],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":50,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.520485,37.966324],\"bearings\":[127,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":51,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":19,\"duration\":13.42,\"duration_typical\":13.42,\"geometry\":\"alglgAjz_uhF{GuF\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519989,37.966034],\"bearing_before\":127,\"bearing_after\":34,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":19,\"announcement\":\"Turn left. Then Your destination will be on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Your destination will be on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":19,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":19.018,\"intersections\":[{\"location\":[-122.519989,37.966034],\"bearings\":[34,307],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":52,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":14.744,\"duration\":17.583,\"duration_typical\":17.583,\"geometry\":\"}tglgAtr_uhFwCrG\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519867,37.966175],\"bearing_before\":34,\"bearing_after\":305,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":14.744,\"announcement\":\"Your destination is on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Your destination is on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":14.744,\"primary\":{\"text\":\"Your destination is on the left\",\"components\":[{\"text\":\"Your destination is on the left\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":18.91,\"intersections\":[{\"location\":[-122.519867,37.966175],\"bearings\":[214,305],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":53,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":0,\"duration\":0,\"duration_typical\":0,\"geometry\":\"uyglgAh{_uhF??\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.520004,37.966251],\"bearing_before\":305,\"bearing_after\":0,\"instruction\":\"Your destination is on the left.\",\"type\":\"arrive\",\"modifier\":\"left\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0,\"intersections\":[{\"location\":[-122.520004,37.966251],\"bearings\":[125],\"entry\":[true],\"in\":0,\"geometry_index\":54,\"admin_index\":0}]}],\"annotation\":{\"distance\":[21.9,13,41.5,70.9,22.7,23.5,6.5,13.4,57.1,42.7,33,10,10,41.4,52.1,8.2,9.6,61.9,18.6,13,51.2,11.5,6.7,10.1,11.1,10.5,8.3,9.2,7.7,7.6,11.3,6,7.7,11.2,21.5,31.6,141.8,26,11.1,21.6,19.2,43.1,17.9,20.3,31.1,21.9,5.8,8.3,26.4,17.6,39.5,54.2,19,14.7],\"congestion\":[\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\"]}}],\"routeOptions\":{\"baseUrl\":\"https:\\/\\/api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237328,37.9753972],[-122.5200609,37.9661962]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"token-here\",\"uuid\":\"4EDxslIYgRqso6fVkA3NC124fG5-200qq3ohWVmcRzqNo4-otP61qw==\"},\"voiceLocale\":\"en-US\"}"
    //     val routeWithOutRoadClassesJson = "{\"routeIndex\":\"0\",\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"geometry\":\"mtylgAd`guhFzJxBl@aH`Ca\\\\je@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@bBgc@\\\\aGNyCLcFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@bLbGdE\\\\|JzAvIl@`WzA~Hl@nIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]{GuFwCrG\",\"weight\":451.469,\"weight_name\":\"auto\",\"legs\":[{\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"summary\":\"Lincoln Avenue, Francisco Boulevard West\",\"admins\":[{\"iso_3166_1\":\"US\",\"iso_3166_1_alpha3\":\"USA\"}],\"steps\":[{\"distance\":22.292,\"duration\":13.375,\"duration_typical\":13.375,\"geometry\":\"mtylgAd`guhFzJxB\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523666,37.975384],\"bearing_before\":0,\"bearing_after\":194,\"instruction\":\"Drive south.\",\"type\":\"depart\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.292,\"announcement\":\"Drive south. Then Turn left onto Laurel Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Drive south. Then Turn left onto Laurel Place.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":22.292,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":15.047,\"intersections\":[{\"location\":[-122.523666,37.975384],\"bearings\":[194],\"entry\":[true],\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":54,\"duration\":27.103,\"duration_typical\":27.103,\"geometry\":\"qhylgA~cguhFl@aH`Ca\\\\\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523727,37.975193],\"bearing_before\":194,\"bearing_after\":101,\"instruction\":\"Turn left onto Laurel Place.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":44,\"announcement\":\"Turn right onto Lincoln Avenue.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Lincoln Avenue.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":54,\"primary\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":34.366,\"intersections\":[{\"location\":[-122.523727,37.975193],\"bearings\":[14,101],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523582,37.97517],\"bearings\":[100,281],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":496,\"duration\":172.634,\"duration_typical\":172.634,\"geometry\":\"acylgAz}euhFje@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523117,37.975105],\"bearing_before\":100,\"bearing_after\":195,\"instruction\":\"Turn right onto Lincoln Avenue.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":480,\"announcement\":\"In a quarter mile, Turn left onto 2nd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In a quarter mile, Turn left onto 2nd Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":101.333,\"announcement\":\"Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":496,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":196.223,\"intersections\":[{\"location\":[-122.523117,37.975105],\"bearings\":[195,280],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523392,37.974293],\"bearings\":[13,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523453,37.974087],\"bearings\":[13,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523499,37.973911],\"bearings\":[12,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523643,37.973412],\"bearings\":[13,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523727,37.973034],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523804,37.972744],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523827,37.972656],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523849,37.972569],\"bearings\":[12,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523933,37.972202],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524055,37.971745],\"bearings\":[12,189],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524071,37.971672],\"bearings\":[9,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524094,37.971588],\"bearings\":[12,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524231,37.971043],\"bearings\":[11,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.524269,37.970879],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":80,\"duration\":23.598,\"duration_typical\":23.598,\"geometry\":\"wsplgAvghuhFbBgc@\\\\aGNyCLcF\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.5243,37.970764],\"bearing_before\":192,\"bearing_after\":96,\"instruction\":\"Turn left onto 2nd Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":71.111,\"announcement\":\"Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":80,\"primary\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":29.888,\"intersections\":[{\"location\":[-122.5243,37.970764],\"bearings\":[12,96],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.52372,37.970715],\"bearings\":[98,276],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.52359,37.970699],\"bearings\":[97,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523514,37.970692],\"bearings\":[95,277],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":286,\"duration\":33.487,\"duration_typical\":33.487,\"geometry\":\"wnplgAnofuhFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@\",\"name\":\"Francisco Boulevard West\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523399,37.970684],\"bearing_before\":95,\"bearing_after\":163,\"instruction\":\"Turn right onto Francisco Boulevard West.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":272.667,\"announcement\":\"In 900 feet, Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 900 feet, Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":66.667,\"announcement\":\"Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":286,\"primary\":{\"text\":\"Irwin Street\",\"components\":[{\"text\":\"Irwin Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":42.61,\"intersections\":[{\"location\":[-122.523399,37.970684],\"bearings\":[163,275],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523331,37.970501],\"bearings\":[137,343],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523193,37.970387],\"bearings\":[132,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.523064,37.970295],\"bearings\":[126,312],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.522903,37.970203],\"bearings\":[140,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":139,\"duration\":34.933,\"duration_typical\":34.933,\"geometry\":\"kpllgAzubuhFbLbGdE\\\\|JzAvIl@`WzA~Hl@\",\"name\":\"Irwin Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521454,37.968662],\"bearing_before\":142,\"bearing_after\":206,\"instruction\":\"Turn right onto Irwin Street.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":129,\"announcement\":\"In 500 feet, Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 500 feet, Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":53.333,\"announcement\":\"Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":139,\"primary\":{\"text\":\"Du Bois Street\",\"components\":[{\"text\":\"Du Bois Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"slight left\"}}],\"driving_side\":\"right\",\"weight\":43.71,\"intersections\":[{\"location\":[-122.521454,37.968662],\"bearings\":[206,322],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521584,37.968452],\"bearings\":[26,187],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521599,37.968353],\"bearings\":[7,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521645,37.968163],\"bearings\":[11,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521667,37.967991],\"bearings\":[6,185],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":224,\"duration\":41.801,\"duration_typical\":41.801,\"geometry\":\"idjlgApgcuhFnIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]\",\"name\":\"Du Bois Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521736,37.967445],\"bearing_before\":188,\"bearing_after\":156,\"instruction\":\"Bear left onto Du Bois Street.\",\"type\":\"turn\",\"modifier\":\"slight left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":214,\"announcement\":\"In 700 feet, Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 700 feet, Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":45.833,\"announcement\":\"Turn left. Then Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":224,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":51.696,\"intersections\":[{\"location\":[-122.521736,37.967445],\"bearings\":[8,156],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521492,37.967026],\"bearings\":[147,334],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521355,37.966862],\"bearings\":[137,327],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521309,37.966824],\"bearings\":[140,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.521248,37.966766],\"bearings\":[134,320],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.520851,37.96653],\"bearings\":[126,298],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0},{\"location\":[-122.520485,37.966324],\"bearings\":[127,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":19,\"duration\":13.42,\"duration_typical\":13.42,\"geometry\":\"alglgAjz_uhF{GuF\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519989,37.966034],\"bearing_before\":127,\"bearing_after\":34,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":19,\"announcement\":\"Turn left. Then Your destination will be on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Your destination will be on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":19,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":19.018,\"intersections\":[{\"location\":[-122.519989,37.966034],\"bearings\":[34,307],\"entry\":[true,false],\"in\":1,\"out\":0,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":14.744,\"duration\":17.583,\"duration_typical\":17.583,\"geometry\":\"}tglgAtr_uhFwCrG\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519867,37.966175],\"bearing_before\":34,\"bearing_after\":305,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":14.744,\"announcement\":\"Your destination is on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Your destination is on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":14.744,\"primary\":{\"text\":\"Your destination is on the left\",\"components\":[{\"text\":\"Your destination is on the left\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":18.91,\"intersections\":[{\"location\":[-122.519867,37.966175],\"bearings\":[214,305],\"entry\":[false,true],\"in\":0,\"out\":1,\"is_urban\":true,\"admin_index\":0}]},{\"distance\":0,\"duration\":0,\"duration_typical\":0,\"geometry\":\"uyglgAh{_uhF??\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.520004,37.966251],\"bearing_before\":305,\"bearing_after\":0,\"instruction\":\"Your destination is on the left.\",\"type\":\"arrive\",\"modifier\":\"left\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0,\"intersections\":[{\"location\":[-122.520004,37.966251],\"bearings\":[125],\"entry\":[true],\"in\":0,\"admin_index\":0}]}],\"annotation\":{\"distance\":[21.9,13,41.5,70.9,22.7,23.5,6.5,13.4,57.1,42.7,33,10,10,41.4,52.1,8.2,9.6,61.9,18.6,13,51.2,11.5,6.7,10.1,11.1,10.5,8.3,9.2,7.7,7.6,11.3,6,7.7,11.2,21.5,31.6,141.8,26,11.1,21.6,19.2,43.1,17.9,20.3,31.1,21.9,5.8,8.3,26.4,17.6,39.5,54.2,19,14.7],\"congestion\":[\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\"]}}],\"routeOptions\":{\"baseUrl\":\"https:\\/\\/api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237328,37.9753972],[-122.5200609,37.9661962]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"token-here\",\"uuid\":\"4EDxslIYgRqso6fVkA3NC124fG5-200qq3ohWVmcRzqNo4-otP61qw==\"},\"voiceLocale\":\"en-US\"}"
    //
    //     val routeWithRoadClasses =  DirectionsRoute.fromJson(routeWithRoadClassesJson)
    //     val routeWithOutRoadClasses =  DirectionsRoute.fromJson(routeWithOutRoadClassesJson)
    //
    //     //getRouteLineTrafficExpressionData(routeWithRoadClasses.legs()!![0])
    //     val resultWithRoadClasses = getRouteLineTrafficExpressionData(routeWithRoadClasses.legs()!![0])
    //     val resultWithOutRoadClasses = getRouteLineTrafficExpressionData(routeWithOutRoadClasses.legs()!![0])
    //
    //     resultWithRoadClasses.size
    // }

    //
    //
    // @Test
    // fun routeTrafficExpTest() {
    //     val routeAsJson = "{\"routeIndex\":\"0\",\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"geometry\":\"mtylgAd`guhFzJxBl@aH`Ca\\\\je@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@bBgc@\\\\aGNyCLcFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@bLbGdE\\\\|JzAvIl@`WzA~Hl@nIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]{GuFwCrG\",\"weight\":451.469,\"weight_name\":\"auto\",\"legs\":[{\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"summary\":\"Lincoln Avenue, Francisco Boulevard West\",\"admins\":[{\"iso_3166_1\":\"US\",\"iso_3166_1_alpha3\":\"USA\"}],\"steps\":[{\"distance\":22.292,\"duration\":13.375,\"duration_typical\":13.375,\"geometry\":\"mtylgAd`guhFzJxB\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523666,37.975384],\"bearing_before\":0,\"bearing_after\":194,\"instruction\":\"Drive south.\",\"type\":\"depart\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.292,\"announcement\":\"Drive south. Then Turn left onto Laurel Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Drive south. Then Turn left onto Laurel Place.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":22.292,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":15.047,\"intersections\":[{\"location\":[-122.523666,37.975384],\"bearings\":[194],\"entry\":[true],\"out\":0,\"geometry_index\":0,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":54,\"duration\":27.103,\"duration_typical\":27.103,\"geometry\":\"qhylgA~cguhFl@aH`Ca\\\\\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523727,37.975193],\"bearing_before\":194,\"bearing_after\":101,\"instruction\":\"Turn left onto Laurel Place.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":44,\"announcement\":\"Turn right onto Lincoln Avenue.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Lincoln Avenue.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":54,\"primary\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":34.366,\"intersections\":[{\"location\":[-122.523727,37.975193],\"bearings\":[14,101],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":1,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.523582,37.97517],\"bearings\":[100,281],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":2,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":496,\"duration\":172.634,\"duration_typical\":172.634,\"geometry\":\"acylgAz}euhFje@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523117,37.975105],\"bearing_before\":100,\"bearing_after\":195,\"instruction\":\"Turn right onto Lincoln Avenue.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":480,\"announcement\":\"In a quarter mile, Turn left onto 2nd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In a quarter mile, Turn left onto 2nd Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":101.333,\"announcement\":\"Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":496,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":196.223,\"intersections\":[{\"location\":[-122.523117,37.975105],\"bearings\":[195,280],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":3,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523392,37.974293],\"bearings\":[13,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":5,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523453,37.974087],\"bearings\":[13,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":6,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523499,37.973911],\"bearings\":[12,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":8,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523643,37.973412],\"bearings\":[13,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":9,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523727,37.973034],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":10,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523804,37.972744],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":11,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523827,37.972656],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":12,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523849,37.972569],\"bearings\":[12,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":13,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523933,37.972202],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":14,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524055,37.971745],\"bearings\":[12,189],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":15,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524071,37.971672],\"bearings\":[9,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":16,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524094,37.971588],\"bearings\":[12,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":17,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524231,37.971043],\"bearings\":[11,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":18,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524269,37.970879],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":19,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}}]},{\"distance\":80,\"duration\":23.598,\"duration_typical\":23.598,\"geometry\":\"wsplgAvghuhFbBgc@\\\\aGNyCLcF\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.5243,37.970764],\"bearing_before\":192,\"bearing_after\":96,\"instruction\":\"Turn left onto 2nd Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":71.111,\"announcement\":\"Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Francisco Boulevard West.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":80,\"primary\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":29.888,\"intersections\":[{\"location\":[-122.5243,37.970764],\"bearings\":[12,96],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":20,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52372,37.970715],\"bearings\":[98,276],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":21,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52359,37.970699],\"bearings\":[97,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":22,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.523514,37.970692],\"bearings\":[95,277],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":23,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}}]},{\"distance\":286,\"duration\":33.487,\"duration_typical\":33.487,\"geometry\":\"wnplgAnofuhFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@\",\"name\":\"Francisco Boulevard West\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523399,37.970684],\"bearing_before\":95,\"bearing_after\":163,\"instruction\":\"Turn right onto Francisco Boulevard West.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":272.667,\"announcement\":\"In 900 feet, Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 900 feet, Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":66.667,\"announcement\":\"Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto Irwin Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":286,\"primary\":{\"text\":\"Irwin Street\",\"components\":[{\"text\":\"Irwin Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":42.61,\"intersections\":[{\"location\":[-122.523399,37.970684],\"bearings\":[163,275],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":24,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523331,37.970501],\"bearings\":[137,343],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":26,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523193,37.970387],\"bearings\":[132,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":28,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523064,37.970295],\"bearings\":[126,312],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":30,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.522903,37.970203],\"bearings\":[140,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":32,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}}]},{\"distance\":139,\"duration\":34.933,\"duration_typical\":34.933,\"geometry\":\"kpllgAzubuhFbLbGdE\\\\|JzAvIl@`WzA~Hl@\",\"name\":\"Irwin Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521454,37.968662],\"bearing_before\":142,\"bearing_after\":206,\"instruction\":\"Turn right onto Irwin Street.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":129,\"announcement\":\"In 500 feet, Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 500 feet, Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":53.333,\"announcement\":\"Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Bear left onto Du Bois Street.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":139,\"primary\":{\"text\":\"Du Bois Street\",\"components\":[{\"text\":\"Du Bois Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"slight left\"}}],\"driving_side\":\"right\",\"weight\":43.71,\"intersections\":[{\"location\":[-122.521454,37.968662],\"bearings\":[206,322],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":37,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521584,37.968452],\"bearings\":[26,187],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":38,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521599,37.968353],\"bearings\":[7,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":39,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521645,37.968163],\"bearings\":[11,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":40,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521667,37.967991],\"bearings\":[6,185],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":41,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":224,\"duration\":41.801,\"duration_typical\":41.801,\"geometry\":\"idjlgApgcuhFnIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]\",\"name\":\"Du Bois Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521736,37.967445],\"bearing_before\":188,\"bearing_after\":156,\"instruction\":\"Bear left onto Du Bois Street.\",\"type\":\"turn\",\"modifier\":\"slight left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":214,\"announcement\":\"In 700 feet, Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 700 feet, Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"},{\"distanceAlongGeometry\":45.833,\"announcement\":\"Turn left. Then Turn left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Turn left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":224,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":51.696,\"intersections\":[{\"location\":[-122.521736,37.967445],\"bearings\":[8,156],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":43,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521492,37.967026],\"bearings\":[147,334],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":45,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521355,37.966862],\"bearings\":[137,327],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":46,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521309,37.966824],\"bearings\":[140,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":47,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521248,37.966766],\"bearings\":[134,320],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":48,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.520851,37.96653],\"bearings\":[126,298],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":50,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.520485,37.966324],\"bearings\":[127,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":51,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":19,\"duration\":13.42,\"duration_typical\":13.42,\"geometry\":\"alglgAjz_uhF{GuF\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519989,37.966034],\"bearing_before\":127,\"bearing_after\":34,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":19,\"announcement\":\"Turn left. Then Your destination will be on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left. Then Your destination will be on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":19,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":19.018,\"intersections\":[{\"location\":[-122.519989,37.966034],\"bearings\":[34,307],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":52,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":14.744,\"duration\":17.583,\"duration_typical\":17.583,\"geometry\":\"}tglgAtr_uhFwCrG\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519867,37.966175],\"bearing_before\":34,\"bearing_after\":305,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":14.744,\"announcement\":\"Your destination is on the left.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Your destination is on the left.<\\/prosody><\\/amazon:effect><\\/speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":14.744,\"primary\":{\"text\":\"Your destination is on the left\",\"components\":[{\"text\":\"Your destination is on the left\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":18.91,\"intersections\":[{\"location\":[-122.519867,37.966175],\"bearings\":[214,305],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":53,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":0,\"duration\":0,\"duration_typical\":0,\"geometry\":\"uyglgAh{_uhF??\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.520004,37.966251],\"bearing_before\":305,\"bearing_after\":0,\"instruction\":\"Your destination is on the left.\",\"type\":\"arrive\",\"modifier\":\"left\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0,\"intersections\":[{\"location\":[-122.520004,37.966251],\"bearings\":[125],\"entry\":[true],\"in\":0,\"geometry_index\":54,\"admin_index\":0}]}],\"annotation\":{\"distance\":[21.9,13,41.5,70.9,22.7,23.5,6.5,13.4,57.1,42.7,33,10,10,41.4,52.1,8.2,9.6,61.9,18.6,13,51.2,11.5,6.7,10.1,11.1,10.5,8.3,9.2,7.7,7.6,11.3,6,7.7,11.2,21.5,31.6,141.8,26,11.1,21.6,19.2,43.1,17.9,20.3,31.1,21.9,5.8,8.3,26.4,17.6,39.5,54.2,19,14.7],\"congestion\":[\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"moderate\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\"]}}],\"routeOptions\":{\"baseUrl\":\"https:\\/\\/api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237328,37.9753972],[-122.5200609,37.9661962]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"token-here\",\"uuid\":\"4EDxslIYgRqso6fVkA3NC124fG5-200qq3ohWVmcRzqNo4-otP61qw==\"},\"voiceLocale\":\"en-US\"}"
    //     val route = DirectionsRoute.fromJson(routeAsJson)
    //     val summedLegDist = route.legs()?.get(0)?.annotation()?.distance()?.sum()
    //
    //     //val indexesOfRoadClasses: List<Pair<Int, String>> = getRoadClassesForRoute(route)
    //
    //     //val trafficInfo = MapRouteLine.MapRouteLineSupport.getTrafficInfoFromLeg(indexesOfRoadClasses, route.legs()!![0])
    //     val trafficInfo = MapRouteLine.MapRouteLineSupport.getTrafficInfoForRoute(route)
    //     val trafficExpressions =  MapRouteLine.MapRouteLineSupport.getTrafficExpressions(trafficInfo, route.distance())
    //
    //     //val distCheck: Double = trafficInfo.map { it.distance }.sum()
    //     val distCheck: Double = trafficInfo.map { it.distance }.sum()
    //
    //     // val expressionStops = mutableListOf<RouteLineExpressionData>()
    //     // var runningDistance = 0.0
    //     // for (index in trafficInfo.indices) {
    //     //     runningDistance += trafficInfo[index].distance
    //     //     val percentDistanceTraveled = (runningDistance / route.distance())
    //     //     expressionStops.add(RouteLineExpressionData(percentDistanceTraveled.toFloat(), 0))
    //     // }
    //     //
    //     //
    //     // val colors = listOf(
    //     //     -27392,
    //     //     -45747,
    //     //     -7396281,
    //     //     -11097861,
    //     //     -11097861
    //     // )
    //     // val trafficExpressions = expressionStops.map {
    //     //     Expression.stop(it.offset.toBigDecimal().setScale(9, BigDecimal.ROUND_DOWN), colors.random())
    //     // }
    //     //
    //     // val stepExp = Expression.step(
    //     //     Expression.lineProgress(),
    //     //     Expression.rgba(0, 0, 0, 0),
    //     //     *trafficExpressions.toTypedArray()
    //     // )
    //     //
    //     // val gradient = PropertyFactory.lineGradient(stepExp)
    //
    //     assertEquals(summedLegDist, distCheck)
    // }

    private fun getMultilegRoute(): DirectionsRoute {
        val routeAsJson = loadJsonFixture("multileg_route.json")
        return DirectionsRoute.fromJson(routeAsJson)
    }

    private fun loadJsonFixture(filename: String): String? {
        val classLoader = javaClass.classLoader
        val inputStream = classLoader?.getResourceAsStream(filename)
        val scanner = Scanner(inputStream, "UTF-8").useDelimiter("\\A")
        return if (scanner.hasNext()) scanner.next() else ""
    }
}
