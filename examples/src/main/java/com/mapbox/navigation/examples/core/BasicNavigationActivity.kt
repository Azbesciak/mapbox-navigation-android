package com.mapbox.navigation.examples.core

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteLeg
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.navigation.base.internal.extensions.applyDefaultParams
import com.mapbox.navigation.base.internal.extensions.coordinates
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.ReplayLocationEngine
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.TripSessionState
import com.mapbox.navigation.core.trip.session.TripSessionStateObserver
import com.mapbox.navigation.examples.R
import com.mapbox.navigation.examples.utils.Utils
import com.mapbox.navigation.examples.utils.Utils.PRIMARY_ROUTE_BUNDLE_KEY
import com.mapbox.navigation.examples.utils.Utils.getRouteFromBundle
import com.mapbox.navigation.examples.utils.extensions.toPoint
import com.mapbox.navigation.ui.camera.NavigationCamera
import com.mapbox.navigation.ui.internal.route.RouteConstants
import com.mapbox.navigation.ui.map.NavigationMapboxMap
import com.mapbox.navigation.ui.route.TrafficInfo
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import kotlinx.android.synthetic.main.activity_basic_navigation_layout.*
import timber.log.Timber
import java.lang.ref.WeakReference
import java.math.BigDecimal

/**
 * This activity shows how to set up a basic turn-by-turn
 * navigation experience with the Navigation SDK and
 * Navigation UI SDK.
 */
open class BasicNavigationActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L
        const val DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5
    }

    private var mapboxNavigation: MapboxNavigation? = null
    private var navigationMapboxMap: NavigationMapboxMap? = null
    private var mapInstanceState: Bundle? = null
    private val mapboxReplayer = MapboxReplayer()
    private var directionRoute: DirectionsRoute? = null

    private val mapStyles = listOf(
        Style.MAPBOX_STREETS,
        Style.OUTDOORS,
        Style.LIGHT,
        Style.DARK,
        Style.SATELLITE_STREETS
    )

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic_navigation_layout)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        val mapboxNavigationOptions = MapboxNavigation
            .defaultNavigationOptionsBuilder(this, Utils.getMapboxAccessToken(this))
            .locationEngine(getLocationEngine())
            .build()

        mapboxNavigation = MapboxNavigation(mapboxNavigationOptions).apply {
            registerTripSessionStateObserver(tripSessionStateObserver)
            registerRouteProgressObserver(routeProgressObserver)
        }

        initListeners()
    }

    //"mapbox://styles/seth-bourget/ckg8g1ihr039n18mipipn5jxr"
    override fun onMapReady(mapboxMap: MapboxMap) {
        mapboxMap.setStyle("mapbox://styles/seth-bourget/ckg8g1ihr039n18mipipn5jxr") {
            mapboxMap.moveCamera(CameraUpdateFactory.zoomTo(15.0))
            navigationMapboxMap = NavigationMapboxMap(mapView, mapboxMap, this, null, true, true)

            mapInstanceState?.let { state ->
                navigationMapboxMap?.restoreStateFrom(state)
            }

            when (directionRoute) {
                null -> {
                    if (shouldSimulateRoute()) {
                        mapboxNavigation
                            ?.registerRouteProgressObserver(ReplayProgressObserver(mapboxReplayer))
                        mapboxReplayer.pushRealLocation(this, 0.0)
                        mapboxReplayer.play()
                    }
                    mapboxNavigation
                        ?.navigationOptions
                        ?.locationEngine
                        ?.getLastLocation(locationListenerCallback)
                    Snackbar
                        .make(
                            container,
                            R.string.msg_long_press_map_to_place_waypoint,
                            LENGTH_SHORT
                        )
                        .show()
                }
                else -> restoreNavigation()
            }
        }


        mapboxMap.addOnMapLongClickListener { latLng ->
            mapboxMap.locationComponent.lastKnownLocation?.let { originLocation ->
                mapboxNavigation?.requestRoutes(
                    RouteOptions.builder().applyDefaultParams()
                        .accessToken(Utils.getMapboxAccessToken(applicationContext))
                        .coordinates(originLocation.toPoint(), null, latLng.toPoint())
                        .alternatives(true)
                        .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
                        .build(),
                    routesReqCallback
                )
            }
            true
        }
    }

    private val routeProgressObserver = object : RouteProgressObserver {
        override fun onRouteProgressChanged(routeProgress: RouteProgress) {
            // do something with the route progress
            Timber.i("route progress: ${routeProgress.currentState}")
        }
    }

    fun startLocationUpdates() {
        if (!shouldSimulateRoute()) {
            val requestLocationUpdateRequest =
                LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                    .setPriority(LocationEngineRequest.PRIORITY_NO_POWER)
                    .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME)
                    .build()

            mapboxNavigation?.navigationOptions?.locationEngine?.requestLocationUpdates(
                requestLocationUpdateRequest,
                locationListenerCallback,
                mainLooper
            )
        }
    }

    private val routesReqCallback = object : RoutesRequestCallback {
        override fun onRoutesReady(routes: List<DirectionsRoute>) {
            if (routes.isNotEmpty()) {



                // routes[0].legs()?.forEach { leg ->
                //     leg.steps()?.forEach { legStep ->
                //         legStep.intersections()?.forEach {  stepIntersection ->
                //             stepIntersection.geometryIndex()
                //             Timber.e("*** geometry index ${stepIntersection.geometryIndex()} has street class ${stepIntersection.mapboxStreetsV8()?.roadClass()}")
                //         }
                //     }
                // }


                // val indexesOfRoadClasses: List<Pair<Int, String>> = routes[0].legs()?.asSequence()
                //     ?.map { leg ->
                //         leg.steps()
                //     }?.filterNotNull()?.flatten()?.map { legStep ->
                //         legStep.intersections()
                //     }?.filterNotNull()?.flatten()
                //     ?.filter {
                //         it.geometryIndex() != null && it.mapboxStreetsV8()?.roadClass() != null
                //     }
                //     ?.map { intersection ->
                //         Pair(intersection.geometryIndex()!!, intersection.mapboxStreetsV8()!!.roadClass()!!)
                //     }?.filterNotNull()?.toList() ?: listOf()



                val summedLegDist = routes[0].legs()?.get(0)?.annotation()?.distance()?.sum()

                //val indexesOfRoadClasses: List<Pair<Int, String>> = routes[0].legs()?.map(::getRoadClassesForLeg)?.flatten() ?: listOf()
                //val trafficInfo = foobar(indexesOfRoadClasses, routes[0].legs()!![0])

                //val distCheck: Double = trafficInfo.map { it.distance }.sum()




                // val routeAsJson = "{\"routeIndex\":\"0\",\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"geometry\":\"mtylgAd`guhFzJxBl@aH`Ca\\\\je@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@bBgc@\\\\aGNyCLcFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@bLbGdE\\\\|JzAvIl@`WzA~Hl@nIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]{GuFwCrG\",\"weight\":451.469,\"weight_name\":\"auto\",\"legs\":[{\"distance\":1335.036,\"duration\":377.934,\"duration_typical\":377.934,\"summary\":\"Lincoln Avenue, Francisco Boulevard West\",\"admins\":[{\"iso_3166_1\":\"US\",\"iso_3166_1_alpha3\":\"USA\"}],\"steps\":[{\"distance\":22.292,\"duration\":13.375,\"duration_typical\":13.375,\"geometry\":\"mtylgAd`guhFzJxB\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523666,37.975384],\"bearing_before\":0.0,\"bearing_after\":194.0,\"instruction\":\"Drive south.\",\"type\":\"depart\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.292,\"announcement\":\"Drive south. Then Turn left onto Laurel Place.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eDrive south. Then Turn left onto Laurel Place.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":22.292,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":15.047,\"intersections\":[{\"location\":[-122.523666,37.975384],\"bearings\":[194],\"entry\":[true],\"out\":0,\"geometry_index\":0,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":54.0,\"duration\":27.103,\"duration_typical\":27.103,\"geometry\":\"qhylgA~cguhFl@aH`Ca\\\\\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523727,37.975193],\"bearing_before\":194.0,\"bearing_after\":101.0,\"instruction\":\"Turn left onto Laurel Place.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":44.0,\"announcement\":\"Turn right onto Lincoln Avenue.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn right onto Lincoln Avenue.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":54.0,\"primary\":{\"text\":\"Lincoln Avenue\",\"components\":[{\"text\":\"Lincoln Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":34.366,\"intersections\":[{\"location\":[-122.523727,37.975193],\"bearings\":[14,101],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":1,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.523582,37.97517],\"bearings\":[100,281],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":2,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":496.0,\"duration\":172.634,\"duration_typical\":172.634,\"geometry\":\"acylgAz}euhFje@hLlKxBzKxBpB^jFz@f^`HrVfDbQvClDl@nDl@zUfDr[rFnC\\\\fDl@ba@pGfIlAbFz@\",\"name\":\"Lincoln Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523117,37.975105],\"bearing_before\":100.0,\"bearing_after\":195.0,\"instruction\":\"Turn right onto Lincoln Avenue.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":480.0,\"announcement\":\"In a quarter mile, Turn left onto 2nd Street.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eIn a quarter mile, Turn left onto 2nd Street.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"},{\"distanceAlongGeometry\":101.333,\"announcement\":\"Turn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn left onto 2nd Street. Then Turn right onto Francisco Boulevard West.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":496.0,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":196.223,\"intersections\":[{\"location\":[-122.523117,37.975105],\"bearings\":[195,280],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":3,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523392,37.974293],\"bearings\":[13,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":5,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523453,37.974087],\"bearings\":[13,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":6,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523499,37.973911],\"bearings\":[12,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":8,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523643,37.973412],\"bearings\":[13,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":9,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523727,37.973034],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":10,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523804,37.972744],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":11,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523827,37.972656],\"bearings\":[12,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":12,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523849,37.972569],\"bearings\":[12,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":13,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.523933,37.972202],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":14,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524055,37.971745],\"bearings\":[12,189],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":15,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524071,37.971672],\"bearings\":[9,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":16,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524094,37.971588],\"bearings\":[12,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":17,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524231,37.971043],\"bearings\":[11,190],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":18,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}},{\"location\":[-122.524269,37.970879],\"bearings\":[10,192],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":19,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"secondary\"}}]},{\"distance\":80.0,\"duration\":23.598,\"duration_typical\":23.598,\"geometry\":\"wsplgAvghuhFbBgc@\\\\aGNyCLcF\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.5243,37.970764],\"bearing_before\":192.0,\"bearing_after\":96.0,\"instruction\":\"Turn left onto 2nd Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":71.111,\"announcement\":\"Turn right onto Francisco Boulevard West.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn right onto Francisco Boulevard West.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":80.0,\"primary\":{\"text\":\"Francisco Boulevard West\",\"components\":[{\"text\":\"Francisco Boulevard West\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":29.888,\"intersections\":[{\"location\":[-122.5243,37.970764],\"bearings\":[12,96],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":20,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52372,37.970715],\"bearings\":[98,276],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":21,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52359,37.970699],\"bearings\":[97,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":22,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.523514,37.970692],\"bearings\":[95,277],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":23,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}}]},{\"distance\":286.0,\"duration\":33.487,\"duration_typical\":33.487,\"geometry\":\"wnplgAnofuhFfE]fDkBpByBpBwCbByBrAiCpBuEbAiBbByB~CwCnIsFhMkLj~@a|@\",\"name\":\"Francisco Boulevard West\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523399,37.970684],\"bearing_before\":95.0,\"bearing_after\":163.0,\"instruction\":\"Turn right onto Francisco Boulevard West.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":272.667,\"announcement\":\"In 900 feet, Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eIn 900 feet, Turn right onto Irwin Street.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"},{\"distanceAlongGeometry\":66.667,\"announcement\":\"Turn right onto Irwin Street.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn right onto Irwin Street.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":286.0,\"primary\":{\"text\":\"Irwin Street\",\"components\":[{\"text\":\"Irwin Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":42.61,\"intersections\":[{\"location\":[-122.523399,37.970684],\"bearings\":[163,275],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":24,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523331,37.970501],\"bearings\":[137,343],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":26,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523193,37.970387],\"bearings\":[132,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":28,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.523064,37.970295],\"bearings\":[126,312],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":30,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.522903,37.970203],\"bearings\":[140,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":32,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}}]},{\"distance\":139.0,\"duration\":34.933,\"duration_typical\":34.933,\"geometry\":\"kpllgAzubuhFbLbGdE\\\\|JzAvIl@`WzA~Hl@\",\"name\":\"Irwin Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521454,37.968662],\"bearing_before\":142.0,\"bearing_after\":206.0,\"instruction\":\"Turn right onto Irwin Street.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":129.0,\"announcement\":\"In 500 feet, Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eIn 500 feet, Bear left onto Du Bois Street.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"},{\"distanceAlongGeometry\":53.333,\"announcement\":\"Bear left onto Du Bois Street.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eBear left onto Du Bois Street.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":139.0,\"primary\":{\"text\":\"Du Bois Street\",\"components\":[{\"text\":\"Du Bois Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"slight left\"}}],\"driving_side\":\"right\",\"weight\":43.71,\"intersections\":[{\"location\":[-122.521454,37.968662],\"bearings\":[206,322],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":37,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521584,37.968452],\"bearings\":[26,187],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":38,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521599,37.968353],\"bearings\":[7,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":39,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521645,37.968163],\"bearings\":[11,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":40,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521667,37.967991],\"bearings\":[6,185],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":41,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":224.0,\"duration\":41.801,\"duration_typical\":41.801,\"geometry\":\"idjlgApgcuhFnIwDvNoHfIsGjA{ApByB~HyLxC}IzK}UbQ}]\",\"name\":\"Du Bois Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.521736,37.967445],\"bearing_before\":188.0,\"bearing_after\":156.0,\"instruction\":\"Bear left onto Du Bois Street.\",\"type\":\"turn\",\"modifier\":\"slight left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":214.0,\"announcement\":\"In 700 feet, Turn left.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eIn 700 feet, Turn left.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"},{\"distanceAlongGeometry\":45.833,\"announcement\":\"Turn left. Then Turn left.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn left. Then Turn left.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":224.0,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":51.696,\"intersections\":[{\"location\":[-122.521736,37.967445],\"bearings\":[8,156],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":43,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521492,37.967026],\"bearings\":[147,334],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":45,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521355,37.966862],\"bearings\":[137,327],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":46,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521309,37.966824],\"bearings\":[140,317],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":47,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.521248,37.966766],\"bearings\":[134,320],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":48,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.520851,37.96653],\"bearings\":[126,298],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":50,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.520485,37.966324],\"bearings\":[127,306],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":51,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":19.0,\"duration\":13.42,\"duration_typical\":13.42,\"geometry\":\"alglgAjz_uhF{GuF\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519989,37.966034],\"bearing_before\":127.0,\"bearing_after\":34.0,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":19.0,\"announcement\":\"Turn left. Then Your destination will be on the left.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eTurn left. Then Your destination will be on the left.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":19.0,\"primary\":{\"text\":\"Turn left\",\"components\":[{\"text\":\"Turn left\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":19.018,\"intersections\":[{\"location\":[-122.519989,37.966034],\"bearings\":[34,307],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":52,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":14.744,\"duration\":17.583,\"duration_typical\":17.583,\"geometry\":\"}tglgAtr_uhFwCrG\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.519867,37.966175],\"bearing_before\":34.0,\"bearing_after\":305.0,\"instruction\":\"Turn left.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":14.744,\"announcement\":\"Your destination is on the left.\",\"ssmlAnnouncement\":\"\\u003cspeak\\u003e\\u003camazon:effect name\\u003d\\\"drc\\\"\\u003e\\u003cprosody rate\\u003d\\\"1.08\\\"\\u003eYour destination is on the left.\\u003c/prosody\\u003e\\u003c/amazon:effect\\u003e\\u003c/speak\\u003e\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":14.744,\"primary\":{\"text\":\"Your destination is on the left\",\"components\":[{\"text\":\"Your destination is on the left\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":18.91,\"intersections\":[{\"location\":[-122.519867,37.966175],\"bearings\":[214,305],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":53,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":0.0,\"duration\":0.0,\"duration_typical\":0.0,\"geometry\":\"uyglgAh{_uhF??\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.520004,37.966251],\"bearing_before\":305.0,\"bearing_after\":0.0,\"instruction\":\"Your destination is on the left.\",\"type\":\"arrive\",\"modifier\":\"left\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0.0,\"intersections\":[{\"location\":[-122.520004,37.966251],\"bearings\":[125],\"entry\":[true],\"in\":0,\"geometry_index\":54,\"admin_index\":0}]}],\"annotation\":{\"distance\":[21.9,13.0,41.5,70.9,22.7,23.5,6.5,13.4,57.1,42.7,33.0,10.0,10.0,41.4,52.1,8.2,9.6,61.9,18.6,13.0,51.2,11.5,6.7,10.1,11.1,10.5,8.3,9.2,7.7,7.6,11.3,6.0,7.7,11.2,21.5,31.6,141.8,26.0,11.1,21.6,19.2,43.1,17.9,20.3,31.1,21.9,5.8,8.3,26.4,17.6,39.5,54.2,19.0,14.7],\"congestion\":[\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\"]}}],\"routeOptions\":{\"baseUrl\":\"https://api.mapbox.com\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237328,37.9753972],[-122.5200609,37.9661962]],\"alternatives\":true,\"language\":\"en\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"pk.eyJ1IjoiZHJpdmVyYXBwLW9zbSIsImEiOiJjazZsOWdneDAwOWhyM2RwZG1ienZzczdpIn0.wjvH50V1roJuB548Yu6Q5Q\",\"uuid\":\"4EDxslIYgRqso6fVkA3NC124fG5-200qq3ohWVmcRzqNo4-otP61qw\\u003d\\u003d\"},\"voiceLocale\":\"en-US\"}"
                // val route = DirectionsRoute.fromJson(routeAsJson)
                //


                directionRoute = routes[0]
                navigationMapboxMap?.drawRoute(routes[0])
                startNavigation.visibility = View.VISIBLE







            } else {
                startNavigation.visibility = View.GONE
            }
        }

        override fun onRoutesRequestFailure(throwable: Throwable, routeOptions: RouteOptions) {
            Timber.e("route request failure %s", throwable.toString())
        }

        override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
            Timber.d("route request canceled")
        }
    }



    @SuppressLint("MissingPermission")
    fun initListeners() {
        startNavigation.setOnClickListener {
            val colors = listOf(
                -27392,
                -45747,
                -7396281,
                -11097861,
                -11097861
            )
            val indexesOfRoadClasses: List<Pair<Int, String>> = directionRoute!!.legs()?.map {
                getRoadClassesForLeg(it)
            }?.flatten() ?: listOf()
            val trafficInfo = foobar(indexesOfRoadClasses, directionRoute!!.legs()!![0])
            val expressionStops = mutableListOf<Pair<Float, Int>>()
            var runningDistance = 0.0
            for (index in trafficInfo.indices) {
                runningDistance += trafficInfo[index].distance
                val percentDistanceTraveled = (runningDistance / directionRoute!!.distance())
                expressionStops.add(Pair(percentDistanceTraveled.toFloat(), colors.random()))
            }

            val trafficExpressions = expressionStops.map {
                Expression.stop(
                it.first.toBigDecimal().setScale(9, BigDecimal.ROUND_DOWN),
                Expression.color(it.second)
                )
            }

            val stepExp = Expression.step(
                Expression.lineProgress(),
                Expression.rgba(0, 0, 0, 0),
                *trafficExpressions.toTypedArray()
            )

            navigationMapboxMap?.retrieveMap()?.style?.getLayer(RouteConstants.PRIMARY_ROUTE_TRAFFIC_LAYER_ID)?.setProperties(
                PropertyFactory.lineGradient(
                    stepExp
                )
            )



            // updateCameraOnNavigationStateChange(true)
            // navigationMapboxMap?.addProgressChangeListener(mapboxNavigation!!)
            // if (mapboxNavigation?.getRoutes()?.isNotEmpty() == true) {
            //     navigationMapboxMap?.startCamera(mapboxNavigation?.getRoutes()!![0])
            // }
            // mapboxNavigation?.startTripSession()
            // startNavigation.visibility = View.GONE
            // stopLocationUpdates()
        }

        fabToggleStyle.setOnClickListener {
            navigationMapboxMap?.retrieveMap()?.setStyle(mapStyles.shuffled().first())
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapboxReplayer.finish()
        mapboxNavigation?.unregisterTripSessionStateObserver(tripSessionStateObserver)
        mapboxNavigation?.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation?.stopTripSession()
        mapboxNavigation?.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        navigationMapboxMap?.saveStateWith(outState)
        mapView.onSaveInstanceState(outState)

        // This is not the most efficient way to preserve the route on a device rotation.
        // This is here to demonstrate that this event needs to be handled in order to
        // redraw the route line after a rotation.
        directionRoute?.let {
            outState.putString(PRIMARY_ROUTE_BUNDLE_KEY, it.toJson())
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        mapInstanceState = savedInstanceState
        directionRoute = getRouteFromBundle(savedInstanceState)
    }

    private val locationListenerCallback = MyLocationEngineCallback(this)

    private fun stopLocationUpdates() {
        if (!shouldSimulateRoute()) {
            mapboxNavigation
                ?.navigationOptions
                ?.locationEngine
                ?.removeLocationUpdates(locationListenerCallback)
        }
    }

    private val tripSessionStateObserver = object : TripSessionStateObserver {
        override fun onSessionStateChanged(tripSessionState: TripSessionState) {
            when (tripSessionState) {
                TripSessionState.STARTED -> {
                    stopLocationUpdates()
                }
                TripSessionState.STOPPED -> {
                    startLocationUpdates()
                    navigationMapboxMap?.hideRoute()
                    updateCameraOnNavigationStateChange(false)
                }
            }
        }
    }

    // Used to determine if the ReplayRouteLocationEngine should be used to simulate the routing.
    // This is used for testing purposes.
    private fun shouldSimulateRoute(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
            .getBoolean(this.getString(R.string.simulate_route_key), false)
    }

    // If shouldSimulateRoute is true a ReplayRouteLocationEngine will be used which is intended
    // for testing else a real location engine is used.
    private fun getLocationEngine(): LocationEngine {
        return if (shouldSimulateRoute()) {
            ReplayLocationEngine(mapboxReplayer)
        } else {
            LocationEngineProvider.getBestLocationEngine(this)
        }
    }

    private fun updateCameraOnNavigationStateChange(
        navigationStarted: Boolean
    ) {
        navigationMapboxMap?.apply {
            if (navigationStarted) {
                updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_GPS)
                updateLocationLayerRenderMode(RenderMode.GPS)
            } else {
                updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_NONE)
                updateLocationLayerRenderMode(RenderMode.COMPASS)
            }
        }
    }

    private class MyLocationEngineCallback(activity: BasicNavigationActivity) :
        LocationEngineCallback<LocationEngineResult> {

        private val activityRef = WeakReference(activity)

        override fun onSuccess(result: LocationEngineResult) {
            activityRef.get()?.navigationMapboxMap?.updateLocation(result.lastLocation)
        }

        override fun onFailure(exception: java.lang.Exception) {
            Timber.i(exception)
        }
    }

    @SuppressLint("MissingPermission")
    private fun restoreNavigation() {
        directionRoute?.let {
            mapboxNavigation?.setRoutes(listOf(it))
            navigationMapboxMap?.addProgressChangeListener(mapboxNavigation!!)
            navigationMapboxMap?.startCamera(mapboxNavigation?.getRoutes()!![0])
            updateCameraOnNavigationStateChange(true)
            mapboxNavigation?.startTripSession()
        }
    }

    fun foobar(indexesOfRoadClasses: List<Pair<Int, String>>, leg: RouteLeg): List<TrafficInfo> {
        val trafficInfo = mutableListOf<TrafficInfo>()
        var index = 0

        while(index < indexesOfRoadClasses.size) {
            val defaultCongestion = leg.annotation()?.congestion()?.get(indexesOfRoadClasses[index].first) ?: "unknown"

            val sectionDistance = if(index + 1 < indexesOfRoadClasses.size) {
                getDistanceBetweenIndexes2(indexesOfRoadClasses[index].first,  indexesOfRoadClasses[index + 1].first, leg)
            } else {
                leg.annotation()?.distance()?.get(index) ?: 0.0
            }
            //val sectionDistance = getDistanceBetweenIndexes2(indexesOfRoadClasses[index].first,  indexesOfRoadClasses[index + 1].first, leg)
            trafficInfo.add(TrafficInfo(defaultCongestion, sectionDistance, indexesOfRoadClasses[index].second))

            index += 1
        }
        return trafficInfo
    }

    // fun sdfasdf(roadClassAndIndex: Pair<Int, String>, leg: RouteLeg) {
    //     val defaultTrafficCongestion = leg.annotation()?.congestion()?.get(roadClassAndIndex.first)
    // }

    private fun getDistanceBetweenIndexes2(startIndex: Int, endIndex: Int, leg: RouteLeg): Double {
        return leg.annotation()?.distance()?.subList(startIndex, endIndex)?.sum() ?: 0.0
    }

    fun getRoadClassesForLeg(leg: RouteLeg): List<Pair<Int, String>> {
        return leg.steps()?.asSequence()?.filterNotNull()?.map { legStep ->
            legStep.intersections()
        }?.filterNotNull()?.flatten()?.filter {
            it.geometryIndex() != null && it.mapboxStreetsV8()?.roadClass() != null
        }?.map {  intersection ->
            Pair(intersection.geometryIndex()!!, intersection.mapboxStreetsV8()!!.roadClass()!!)
        }?.toList() ?: listOf()
    }
}


